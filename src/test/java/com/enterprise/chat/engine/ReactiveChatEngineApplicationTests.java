package com.enterprise.chat.engine;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.enterprise.chat.engine.service.RateLimiter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReactiveChatEngineApplicationTests.TestRateLimitConfig.class)
class ReactiveChatEngineApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void registrationCanCreateAndAccessARoom() throws Exception {
		String response = mockMvc.perform(post("/api/auth/register")
				.contentType("application/json")
				.content("""
						{"email":"test@example.com","displayName":"Test User","password":"long-enough-password"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String token = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(response).get("accessToken").asText();
		String roomResponse = mockMvc.perform(post("/api/rooms")
				.header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"name\":\"Architecture\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String roomId = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(roomResponse).get("id").asText();

		String teammateResponse = mockMvc.perform(post("/api/auth/register")
				.contentType("application/json")
				.content("""
						{"email":"teammate@example.com","displayName":"Teammate","password":"long-enough-password"}
						"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String teammateToken = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(teammateResponse).get("accessToken").asText();
		mockMvc.perform(get("/api/rooms/public").header("Authorization", "Bearer " + teammateToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(roomId));
		mockMvc.perform(post("/api/rooms/" + roomId + "/join")
				.header("Authorization", "Bearer " + teammateToken))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/rooms/public").header("Authorization", "Bearer " + teammateToken))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));

		mockMvc.perform(get("/api/rooms/" + roomId + "/messages")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void roomOwnersCanManagePrivacyInvitesMembersAndRoomLifecycle() throws Exception {
		String ownerToken = registerAndGetToken("owner@example.com", "Owner");
		String roomResponse = mockMvc.perform(post("/api/rooms")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType("application/json")
				.content("{\"name\":\"Private Planning\",\"isPublic\":false}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("OWNER"))
				.andExpect(jsonPath("$.isPublic").value(false))
				.andReturn().getResponse().getContentAsString();
		var room = new com.fasterxml.jackson.databind.ObjectMapper().readTree(roomResponse);
		String roomId = room.get("id").asText();
		String oldInviteCode = room.get("inviteCode").asText();

		mockMvc.perform(patch("/api/rooms/" + roomId + "/settings")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType("application/json")
				.content("{\"isPublic\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isPublic").value(false));
		String rotatedRoom = mockMvc.perform(post("/api/rooms/" + roomId + "/invite/rotate")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String inviteCode = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(rotatedRoom).get("inviteCode").asText();
		org.junit.jupiter.api.Assertions.assertNotEquals(oldInviteCode, inviteCode);

		String memberToken = registerAndGetToken("member@example.com", "Member");
		mockMvc.perform(post("/api/rooms/" + roomId + "/join?invite=" + oldInviteCode)
				.header("Authorization", "Bearer " + memberToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/rooms/" + roomId + "/join?invite=" + inviteCode)
				.header("Authorization", "Bearer " + memberToken))
				.andExpect(status().isOk());

		long memberId = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(sessionFor("member@example.com")).get("userId").asLong();
		mockMvc.perform(delete("/api/rooms/" + roomId + "/memberships/current")
				.header("Authorization", "Bearer " + memberToken))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/rooms/" + roomId + "/join?invite=" + inviteCode)
				.header("Authorization", "Bearer " + memberToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/rooms/" + roomId + "/members/" + memberId + "/ban")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/rooms/" + roomId + "/join?invite=" + inviteCode)
				.header("Authorization", "Bearer " + memberToken))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/rooms/" + roomId + "/memberships/current")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/rooms/" + roomId + "/join")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("OWNER"));

		mockMvc.perform(delete("/api/rooms/" + roomId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/rooms/" + roomId + "/join?invite=" + inviteCode)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNotFound());
	}

	private String registerAndGetToken(String email, String displayName) throws Exception {
		String response = mockMvc.perform(post("/api/auth/register")
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"displayName\":\"" + displayName
						+ "\",\"password\":\"long-enough-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("accessToken").asText();
	}

	private String sessionFor(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"long-enough-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	@TestConfiguration
	static class TestRateLimitConfig {
		@Bean
		@Primary
		RateLimiter testRateLimiter() {
			return new RateLimiter() {
				@Override
				public boolean allow(long userId, String eventType) { return true; }
				@Override
				public boolean allow(String subject, String eventType, int limit) { return true; }
			};
		}
	}
}
