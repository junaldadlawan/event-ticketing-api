package com.junaldadlawan.event_ticketing_api.upload;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end for image uploads, through the real security filter chain: sign-in
 * is required to upload, the stored file is then served publicly, and only real
 * images are accepted. Files go to a throwaway folder under {@code target/}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.upload.dir=target/test-uploads",
        "app.upload.public-base-url="
})
class UploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token() {
        return jwtService.generateAccessToken(User.builder()
                .id(UUID.randomUUID())
                .email("uploader-" + UUID.randomUUID() + "@test.local")
                .role(Role.CUSTOMER)
                .build());
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(60, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private MockMultipartFile part(String name, String claimedType, byte[] bytes) {
        return new MockMultipartFile("file", name, claimedType, bytes);
    }

    @Test
    void upload_signedIn_returns201WithAnAbsoluteUrl_andTheFileIsServedPublicly() throws Exception {
        byte[] bytes = png();

        MvcResult result = mockMvc.perform(multipart("/api/v1/uploads").file(part("bg.png", "image/png", bytes))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(bytes.length))
                .andReturn();

        String url = objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
        assertThat(url).startsWith("http://localhost/api/v1/uploads/files/").endsWith(".png");

        // no Authorization header: buyers and scanners render the ticket without any special access
        MvcResult served = mockMvc.perform(get(URI.create(url).getPath()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void upload_withoutSigningIn_isRejected_andNothingIsStored() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads").file(part("bg.png", "image/png", png())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_aTextFileCalledPng_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(part("bg.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_svg_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(part("bg.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_withoutAFilePart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serve_unknownName_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/files/{name}", UUID.randomUUID() + ".png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serve_pathTraversalAndOddNames_return404() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/files/{name}", "..%2F..%2Fapplication.properties"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/uploads/files/{name}", "application.properties"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/uploads/files/{name}", UUID.randomUUID() + ".svg"))
                .andExpect(status().isNotFound());
    }
}
