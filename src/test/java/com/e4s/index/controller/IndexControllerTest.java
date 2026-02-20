package com.e4s.index.controller;

import com.e4s.index.model.Granularity;
import com.e4s.index.service.IndexService;
import com.e4s.index.service.IndexStats;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IndexController.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IndexService indexService;

    @Test
    void createIndex_shouldCallService() throws Exception {
        String requestBody = """
                {"indexName": "meter-data"}
                """;

        mockMvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(indexService).createIndex("meter-data");
    }

    @Test
    void createIndex_shouldValidateIndexName() throws Exception {
        String requestBody = """
                {"indexName": "invalid name!"}
                """;

        mockMvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIndexes_shouldReturnList() throws Exception {
        when(indexService.listIndexes()).thenReturn(List.of("index1", "index2"));

        mockMvc.perform(get("/api/v1/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("index1"))
                .andExpect(jsonPath("$[1]").value("index2"));
    }

    @Test
    void getIndex_shouldReturnInfo() throws Exception {
        when(indexService.indexExists("meter-data")).thenReturn(true);
        when(indexService.getStats("meter-data")).thenReturn(
                new IndexStats(100L, 50, 1024L)
        );

        mockMvc.perform(get("/api/v1/index/meter-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("meter-data"))
                .andExpect(jsonPath("$.entityCount").value(100));
    }

    @Test
    void getIndex_shouldReturn404WhenNotFound() throws Exception {
        when(indexService.indexExists("unknown")).thenReturn(false);

        mockMvc.perform(get("/api/v1/index/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIndex_shouldCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/index/meter-data"))
                .andExpect(status().isOk());

        verify(indexService).deleteIndex("meter-data");
    }

    @Test
    void exists_shouldReturnTrue() throws Exception {
        when(indexService.exists(eq("meter-data"), eq(1L), eq(Granularity.DAY), anyInt()))
                .thenReturn(true);

        String requestBody = """
                {"indexName": "meter-data", "entityId": 1, "granularity": "DAY", "timestamp": 1704067200000}
                """;

        mockMvc.perform(post("/api/v1/index/exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.timestamp").value(1704067200000L));
    }

    @Test
    void findPrev_shouldReturnValue() throws Exception {
        when(indexService.findPrev(eq("meter-data"), eq(1L), eq(Granularity.DAY), anyInt()))
                .thenReturn(Optional.of(20000));

        String requestBody = """
                {"indexName": "meter-data", "entityId": 1, "granularity": "DAY", "timestamp": 1704067200000}
                """;

        mockMvc.perform(post("/api/v1/index/prev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists());
    }

    @Test
    void findNext_shouldReturnValue() throws Exception {
        when(indexService.findNext(eq("meter-data"), eq(1L), eq(Granularity.DAY), anyInt()))
                .thenReturn(Optional.of(20002));

        String requestBody = """
                {"indexName": "meter-data", "entityId": 1, "granularity": "DAY", "timestamp": 1704067200000}
                """;

        mockMvc.perform(post("/api/v1/index/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists());
    }

    @Test
    void mark_shouldCallService() throws Exception {
        String requestBody = """
                {"indexName": "meter-data", "entityId": 1, "granularity": "DAY", "timestamps": [1704067200000, 1704153600000]}
                """;

        mockMvc.perform(post("/api/v1/index/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(indexService).markBatch(eq("meter-data"), eq(1L), eq(Granularity.DAY), any(int[].class));
    }

    @Test
    void evictEntity_shouldCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/index/meter-data/entity/1"))
                .andExpect(status().isOk());

        verify(indexService).evictEntity("meter-data", 1L);
    }

    @Test
    void evictIndex_shouldCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/index/meter-data/cache"))
                .andExpect(status().isOk());

        verify(indexService).evictIndex("meter-data");
    }

    @Test
    void exists_withMonthGranularity_shouldWork() throws Exception {
        when(indexService.exists(eq("meter-data"), eq(1L), eq(Granularity.MONTH), anyInt()))
                .thenReturn(true);

        String requestBody = """
                {"indexName": "meter-data", "entityId": 1, "granularity": "MONTH", "timestamp": 1704067200000}
                """;

        mockMvc.perform(post("/api/v1/index/exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void exists_withYearGranularity_shouldWork() throws Exception {
        when(indexService.exists(eq("meter-data"), eq(1L), eq(Granularity.YEAR), anyInt()))
                .thenReturn(true);

        String requestBody = """
                {"indexName": "meter-data", "entityId": 1, "granularity": "YEAR", "timestamp": 1704067200000}
                """;

        mockMvc.perform(post("/api/v1/index/exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }
}
