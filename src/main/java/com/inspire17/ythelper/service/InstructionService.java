package com.inspire17.ythelper.service;

import com.inspire17.ythelper.document.AdminInstructions;
import com.inspire17.ythelper.dto.*;
import com.inspire17.ythelper.entity.AudioEntity;
import com.inspire17.ythelper.entity.UserEntity;
import com.inspire17.ythelper.entity.VideoEntity;
import com.inspire17.ythelper.exceptions.ServerException;
import com.inspire17.ythelper.repository.AudioRepository;
import com.inspire17.ythelper.repository.InstructionRepository;
import com.inspire17.ythelper.repository.UserRepository;
import com.inspire17.ythelper.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstructionService {
    @Autowired
    private final AudioRepository audioRepository;

    @Autowired
    private final CloudStorageService cloudStorageService; // Handles S3, GCP, etc.
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final VideoRepository videoRepository;
    @Autowired
    private final InstructionRepository instructionRepository;

    @Value("${storage.env}")
    private String storageEnv;

    @Value("${audio.local.path}")
    private String localStoragePath;

    @Transactional(rollbackFor = {Exception.class})
    public InstructionDto uploadAudioInstruction(MultipartFile file, String videoId, AccountInfoDto accountInfo) throws IOException {
        String uniqueId = UUID.randomUUID().toString();
        String filePath;
        File tempFile = Files.createTempFile(uniqueId, "." + "mp3").toFile();
        file.transferTo(tempFile);

        try {
            if ("HOST_MACHINE".equals(storageEnv)) {
                filePath = saveToLocal(file, uniqueId);
            } else {
                filePath = cloudStorageService.uploadFile(tempFile, uniqueId, storageEnv);
            }


            // Step 3: Store Video Entry in PostgreSQL
            Optional<UserEntity> userEntity = userRepository.findByUsername(accountInfo.getName());
            if (userEntity.isEmpty()) {
                throw new ServerException("Failed to verify user", 403);
            }

            Optional<VideoEntity> videoEntity = videoRepository.findById(videoId);

            if (videoEntity.isEmpty()) {
                throw new ServerException("Failed to upload audio instruction, video is not uploaded", 403);
            }

            AdminInstructions adminInstructions = new AdminInstructions();
            adminInstructions.setId(UUID.randomUUID().toString());
            adminInstructions.setVideoId(videoId);
            adminInstructions.setInstructionType(InstructionType.AUDIO);
            adminInstructions.setContent(filePath);

            instructionRepository.save(adminInstructions);


            AudioEntity audioEntity = new AudioEntity();
            audioEntity.setId(uniqueId);
            audioEntity.setAudioType(AudioType.INSTRUCTIONS);
            audioEntity.setUploadedBy(userEntity.get());
            audioEntity.setInstructionId(adminInstructions.getId());
            audioEntity.setFilePath(filePath);


            audioRepository.save(audioEntity);
            log.info("Audio instruction uploaded successfully with ID: {}", uniqueId);


            InstructionDto instructionDto = new InstructionDto();
            instructionDto.setInstructionId(adminInstructions.getId());
            instructionDto.setType(InstructionType.AUDIO);
            instructionDto.setContent(filePath);
            instructionDto.setVideoId(videoId);

            return instructionDto;
        } catch (Exception e) {
            log.error("Error uploading video: {}", e.getMessage(), e);
            throw new IOException("Failed to upload video", e);
        }
    }

    private String saveToLocal(MultipartFile file, String uniqueId) throws IOException {
        Path uploadDir = Path.of(localStoragePath);
        if (Files.notExists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        Path filePath = uploadDir.resolve(uniqueId + ".mp3");
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Video saved locally at: {}", filePath);
        return filePath.toString();
    }

    public Resource getResource(String id, AccountInfoDto accountInfo) {
        Optional<VideoEntity> videoEntity = videoRepository.findById(id);
        if (videoEntity.isEmpty()) {
            throw new ServerException("Video not found", 404);
        }

        Path filePath = Paths.get(videoEntity.get().getMp4filePath());
        Resource resource = null;
        try {
            resource = new UrlResource(filePath.toUri());
            return resource;
        } catch (MalformedURLException e) {
            log.error("Malformed url error: {}", e.getMessage());
            throw new ServerException("Video not found", 404);
        }

    }

    public InstructionDto saveTextInstruction(TextInstructionDto textInstructionDto, AccountInfoDto accountInfo) {

        Optional<UserEntity> userEntity = userRepository.findByUsername(accountInfo.getName());
        if (userEntity.isEmpty()) {
            throw new ServerException("Failed to verify user", 403);
        }

        Optional<VideoEntity> videoEntity = videoRepository.findById(textInstructionDto.getVideoId());

        if (videoEntity.isEmpty()) {
            throw new ServerException("Failed to upload audio instruction, video is not uploaded", 403);
        }

        AdminInstructions adminInstructions = new AdminInstructions();
        adminInstructions.setId(UUID.randomUUID().toString());
        adminInstructions.setVideoId(textInstructionDto.getVideoId());
        adminInstructions.setInstructionType(InstructionType.TEXT);
        adminInstructions.setContent(textInstructionDto.getText());

        instructionRepository.save(adminInstructions);

        InstructionDto instructionDto = new InstructionDto();
        instructionDto.setInstructionId(adminInstructions.getId());
        instructionDto.setType(InstructionType.TEXT);
        instructionDto.setVideoId(textInstructionDto.getVideoId());
        instructionDto.setContent(textInstructionDto.getText());

        return instructionDto;

    }
}
