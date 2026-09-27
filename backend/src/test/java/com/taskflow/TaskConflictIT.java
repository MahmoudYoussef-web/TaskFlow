package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two sessions editing the same task: the second write with a stale version
 * gets a clean 409 — not a crash, not a silent lost update.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskConflictIT extends ContainersBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void staleVersionSecondWrite_gets409() throws Exception {
        String email = "c" + System.nanoTime() + "@x.com";
        String reg = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"C"}"""
                                .formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String access = om.readTree(reg).get("accessToken").asText();

        String created = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Conflict task","priority":"HIGH"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode task = om.readTree(created);
        String id = task.get("id").asText();
        long version = task.get("version").asLong();
        assertThat(task.has("version")).isTrue(); // contract: version always present

        // Session A writes with the fresh version → 200.
        mvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"title":"Session A wins"}""".formatted(version)))
                .andExpect(status().isOk());

        // Session B still holds the old version → 409 with VERSION_CONFLICT.
        String conflict = mvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"title":"Session B loses"}""".formatted(version)))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(conflict).get("code").asText()).isEqualTo("VERSION_CONFLICT");

        // Drag endpoint honors the same rule.
        mvc.perform(patch("/api/v1/tasks/" + id + "/status")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","version":%d}""".formatted(version)))
                .andExpect(status().isConflict());

        // Audit trail recorded the update.
        String history = mvc.perform(get("/api/v1/tasks/" + id + "/history")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(history).get("content").size()).isGreaterThanOrEqualTo(1);
    }
}
