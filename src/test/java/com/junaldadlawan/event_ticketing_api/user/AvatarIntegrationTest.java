package com.junaldadlawan.event_ticketing_api.user;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end for profile pictures: only an image uploaded through this server is accepted, the replaced or removed
 * file is cleaned up unless something else still uses it, and {@code avatarUrl} comes back wherever a user is shown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.upload.dir=target/test-uploads",
        "app.upload.public-base-url="
})
class AvatarIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<UUID> userIds = new ArrayList<>();
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = newUser();
        token = "Bearer " + jwtService.generateAccessToken(user);
    }

    @AfterEach
    void tearDown() {
        userIds.forEach(userRepository::deleteById);
        userIds.clear();
    }

    private User newUser() {
        User saved = userRepository.save(User.builder().firstName("Avatar").lastName("User")
                .email("avatar-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("irrelevant")).role(Role.CUSTOMER).build());
        userIds.add(saved.getId());
        return saved;
    }

    private String upload() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "png", out);
        MvcResult result = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "me.png", "image/png", out.toByteArray()))
                        .header("Authorization", token))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
    }

    private void setAvatar(String authorization, String avatarUrl) throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", authorization).contentType("application/json")
                        .content("{\"avatarUrl\":\"" + avatarUrl + "\"}"))
                .andExpect(status().isOk());
    }

    private int fileStatus(String url) throws Exception {
        return mockMvc.perform(get(URI.create(url).getPath())).andReturn().getResponse().getStatus();
    }

    @Test
    void ownUploadedImage_becomesTheProfilePicture_andIsReturnedByMeAndInTheAdminUserList() throws Exception {
        String url = upload();

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"avatarUrl\":\"" + url + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(url));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(url));
        String admin = "Bearer " + jwtService.generateAccessToken(
                User.builder().id(UUID.randomUUID()).email("avatar-admin@test.local").role(Role.ADMIN).build());
        mockMvc.perform(get("/api/v1/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + user.getId() + "')].avatarUrl").value(url));
        assertThat(fileStatus(url)).isEqualTo(200);
    }

    @Test
    void aUserWithoutAPicture_hasANullAvatarUrl() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
    }

    @Test
    void linksThatAreNotOurUploads_areRejectedWith400_andNothingChanges() throws Exception {
        String own = upload();
        setAvatar(token, own);
        String fileName = own.substring(own.lastIndexOf('/') + 1);
        String missing = "http://localhost/api/v1/uploads/files/" + UUID.randomUUID() + ".png";

        for (String bad : new String[] {
                "https://evil.example.com/pixel.png",
                "http://evil.example.com/api/v1/uploads/files/" + fileName,
                "http://localhost/api/v1/uploads/files/" + fileName + "?x=1",
                "http://localhost/api/v1/uploads/files/" + fileName + "#frag",
                "http://localhost/api/v1/uploads/files/../" + fileName,
                "http://localhost/other/" + fileName,
                "http://localhost/api/v1/uploads/files/not-a-stored-name.png",
                "javascript:alert(1)",
                "/api/v1/uploads/files/" + fileName,
                missing}) {
            mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                            .content("{\"avatarUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(jsonPath("$.avatarUrl").value(own));
        assertThat(fileStatus(own)).isEqualTo(200);
    }

    @Test
    void anAvatarUrlOver500Characters_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"avatarUrl\":\"http://localhost/" + "a".repeat(500) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacingThePicture_deletesTheOldFile() throws Exception {
        String first = upload();
        setAvatar(token, first);
        String second = upload();

        setAvatar(token, second);

        assertThat(fileStatus(first)).isEqualTo(404);
        assertThat(fileStatus(second)).isEqualTo(200);
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(jsonPath("$.avatarUrl").value(second));
    }

    @Test
    void replacingThePicture_keepsTheOldFileWhenAnotherUserStillUsesIt() throws Exception {
        String shared = upload();
        setAvatar(token, shared);
        User other = newUser();
        setAvatar("Bearer " + jwtService.generateAccessToken(other), shared);
        String second = upload();

        setAvatar(token, second);

        assertThat(fileStatus(shared)).isEqualTo(200);
    }

    @Test
    void anEmptyAvatarUrl_removesThePicture_andDeletesTheFile() throws Exception {
        String url = upload();
        setAvatar(token, url);

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(jsonPath("$.avatarUrl").isEmpty());
        assertThat(fileStatus(url)).isEqualTo(404);
    }

    @Test
    void updatingOtherFields_leavesThePictureAlone() throws Exception {
        String url = upload();
        setAvatar(token, url);

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"firstName\":\"New\",\"lastName\":\"Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.avatarUrl").value(url));
        assertThat(fileStatus(url)).isEqualTo(200);
    }

    @Test
    void sendingTheSamePictureAgain_keepsTheFile() throws Exception {
        String url = upload();
        setAvatar(token, url);

        setAvatar(token, url);

        assertThat(fileStatus(url)).isEqualTo(200);
    }

    @Test
    void settingAPicture_needsSignIn() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").contentType("application/json").content("{\"avatarUrl\":\"\"}"))
                .andExpect(status().isUnauthorized());
    }
}
