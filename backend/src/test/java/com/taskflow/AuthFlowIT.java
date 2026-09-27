package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT extends ContainersBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void register_login_refresh_rotation_me() throws Exception {
        String email = "u" + System.nanoTime() + "@x.com";

        String reg = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"U"}"""
                                .formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode regJson = om.readTree(reg);

        String login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}""".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = om.readTree(login).get("refreshToken").asText();
        String access = om.readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // Rotation: use refresh once → new pair; reuse of the old one must fail.
        String rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refresh)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assert om.readTree(rotated).has("accessToken");

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refresh)))
                .andExpect(status().isBadRequest());

        // Duplicate registration is rejected.
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"U2"}"""
                                .formatted(email)))
                .andExpect(status().isBadRequest());
    }
}
