package com.inspire17.ythelper.service;

import com.inspire17.ythelper.document.AdminInstructions;
import com.inspire17.ythelper.document.VideoMetadata;
import com.inspire17.ythelper.dto.AccountInfoDto;
import com.inspire17.ythelper.dto.VideoDto;
import com.inspire17.ythelper.dto.VideoMetaDataDto;
import com.inspire17.ythelper.dto.VideoStatus;
import com.inspire17.ythelper.entity.ChannelEntity;
import com.inspire17.ythelper.entity.UserEntity;
import com.inspire17.ythelper.entity.VideoConversionStatusEntity;
import com.inspire17.ythelper.entity.VideoEntity;
import com.inspire17.ythelper.exceptions.ServerException;
import com.inspire17.ythelper.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
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
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    @Autowired
    private final VideoConversionService videoConverterService;

    @Autowired
    private final VideoRepository videoRepository;
    @Autowired
    private final VideoConversionStatusRepository videoConversionRepository;
    @Autowired
    private final VideoMetadataRepository videoMetadataRepository;
    @Autowired
    private final CloudStorageService cloudStorageService; // Handles S3, GCP, etc.
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final InstructionRepository instructionRepository;

    @Autowired
    private final ChannelRepository channelRepository;

    @Autowired
    private final ModelMapper modelMapper;

    @Value("${storage.env}")
    private String storageEnv;

    @Value("${video.local.path}")
    private String localStoragePath;

    @Transactional(rollbackFor = {Exception.class})
    public String uploadVideo(MultipartFile file, String fileExtension, String title, String channelId, AccountInfoDto accountInfo) throws IOException {
        String uniqueId = UUID.randomUUID().toString();
        Map<String, String> filePath;
        final VideoEntity video = new VideoEntity();
        video.setId(uniqueId);

        try {
            if ("HOST_MACHINE".equals(storageEnv)) {
                filePath = saveToLocal(file, fileExtension, uniqueId);
            } else {
                filePath = saveToCloud(file, fileExtension, uniqueId);
            }

            // Step 3: Store Video Entry in PostgreSQL
            Optional<UserEntity> userEntity = userRepository.findByUsername(accountInfo.getName());
            if (userEntity.isEmpty()) {
                throw new ServerException("Failed to verify user", 403);
            }

            Optional<ChannelEntity> channelEntity = channelRepository.findById(Long.valueOf(channelId));

            if (channelEntity.isEmpty()) {
                throw new ServerException("Failed to verify channel", 403);
            }


            video.setTitle(title);
            video.setParentId(null);
            video.setUploadedBy(userEntity.get());
            video.setChannel(channelEntity.get());
            video.setStatus(VideoStatus.TODO);
            video.setRevisionId(1);
            video.setUploadedAt(LocalDateTime.now());
            video.setOriginalFilePath(filePath.get("original"));
            video.setMp4filePath(filePath.get("mp4"));

            videoRepository.save(video);
            VideoConversionStatusEntity conversionStatusEntity = new VideoConversionStatusEntity();
            conversionStatusEntity.setVideo(video);
            conversionStatusEntity.setStatus(false);

            videoConversionRepository.save(conversionStatusEntity);

            log.info("Video uploaded successfully with ID: {}", uniqueId);
            return uniqueId;
        } catch (Exception e) {
            log.error("Error uploading video: {}", e.getMessage(), e);
            throw new IOException("Failed to upload video", e);
        }
    }


    private Map<String, String> saveToLocal(MultipartFile file, String fileExtension, String uniqueId) throws IOException {
        Map<String, String> filePaths = new HashMap<>();

        Path uploadDir = Paths.get(localStoragePath);
        if (Files.notExists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        Path originalDir = uploadDir.resolve("original");
        if (Files.notExists(originalDir)) {
            Files.createDirectories(originalDir);
        }

        Path originalFilePath = originalDir.resolve(uniqueId + "." + fileExtension);
        try {
            Files.copy(file.getInputStream(), originalFilePath, StandardCopyOption.REPLACE_EXISTING);
            filePaths.put("original", originalFilePath.toString());
            log.info("Video saved locally at: {}", originalFilePath);
        } catch (IOException e) {
            log.error("Failed to save original video locally: {}", e.getMessage());
            throw new IOException("Failed to save original video", e);
        }

        // Convert to MP4 only if it's not already an MP4 file
        if (!fileExtension.equalsIgnoreCase("mp4")) {
            // Ensure "mp4" directory exists
            Path mp4Dir = uploadDir.resolve("mp4");
            if (Files.notExists(mp4Dir)) {
                Files.createDirectories(mp4Dir);
            }

            Path mp4FilePath = mp4Dir.resolve(uniqueId + ".mp4");

            File inputFile = originalFilePath.toFile();
            File outputFile = mp4FilePath.toFile();

            videoConverterService.convertToMP4(inputFile, outputFile, uniqueId);
            filePaths.put("mp4", mp4FilePath.toString());


        } else {
            filePaths.put("mp4", originalFilePath.toString()); // Already in MP4 format
        }

        return filePaths;
    }


    private Map<String, String> saveToCloud(MultipartFile file, String fileExtension, String uniqueId) throws IOException {
        Map<String, String> filePaths = new HashMap<>();
        File tempFile = null;
        File tempMp4File = null;

        try {
            // ✅ Create a temp file for the original upload
            tempFile = Files.createTempFile(uniqueId, "." + fileExtension).toFile();
            file.transferTo(tempFile);

            // ✅ Upload original high-resolution video
            String originalFilePath = cloudStorageService.uploadFile(tempFile, uniqueId + "." + fileExtension, storageEnv);
            filePaths.put("original", originalFilePath);
            log.info("✅ Video saved to cloud at: {}", originalFilePath);

            // ✅ Convert and upload MP4 version if needed
            if (!fileExtension.equalsIgnoreCase("mp4")) {
                tempMp4File = Files.createTempFile(uniqueId, ".mp4").toFile();
                videoConverterService.convertToMP4AndUpload(tempFile, tempMp4File, cloudStorageService, uniqueId, storageEnv);

            } else {
                filePaths.put("mp4", originalFilePath); // Already MP4, no conversion needed
            }
        } catch (IOException e) {
            log.error("❌ Error while saving video to cloud: {}", e.getMessage());
            throw new IOException("Failed to upload video", e);
        } finally {
            // ✅ Cleanup temporary files
            if (tempFile != null && tempFile.exists()) tempFile.delete();
            if (tempMp4File != null && tempMp4File.exists()) tempMp4File.delete();
        }

        return filePaths;
    }


    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
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

    public boolean postMetaData(VideoMetaDataDto metaDataDto, AccountInfoDto accountInfo) {
        if (metaDataDto.getId() != null) {
            throw new ServerException("Invalid request, metadata id is self assigned", 400);
        }
        VideoMetadata videoMetadata = modelMapper.map(metaDataDto, VideoMetadata.class);

        if (videoMetadata == null) {
            throw new ServerException("Invalid request", 400);
        }

        if (validateMetadata(metaDataDto, accountInfo)) {
            videoMetadataRepository.save(videoMetadata);
            return true;
        }
        return false;
    }

    private boolean validateMetadata(VideoMetaDataDto metaDataDto, AccountInfoDto accountInfo) {
        String videoId = metaDataDto.getVideoId();
        Optional<VideoEntity> videoEntity = videoRepository.findById(videoId);
        if (videoEntity.isEmpty()) {
            throw new ServerException("Video not found", 400);
        }

        if (!videoEntity.map(entity -> entity.getUploadedBy().getUsername().equals(accountInfo.getName())).orElse(false)) {
            throw new ServerException("Failed to validate uploaded by for the video", 400);
        }

        metaDataDto.getEditorInstructions().forEach(instructionDto -> {
            String instructionId = instructionDto.getInstructionId();
            Optional<AdminInstructions> instruction = instructionRepository.findById(instructionId);
            if (instruction.isEmpty()) {
                throw new ServerException("Video instruction are invalid", 400);
            }
            if (!instruction.get().getVideoId().equals(metaDataDto.getVideoId())) {
                throw new ServerException("Instruction doesn't belong to this video", 400);
            }
        });

        return false;
    }

    public List<VideoDto> getResources(String channelId, AccountInfoDto accountInfo) {
        Optional<ChannelEntity> channelEntity = channelRepository.findById(Long.valueOf(channelId));

        List<VideoDto> videoDtos = new ArrayList<>();
        if (channelEntity.isEmpty()) {
            return videoDtos;
        }

        List<VideoEntity> videos = videoRepository.findByChannel(channelEntity.get());
        videos.forEach(v -> {
            VideoDto videoDto = new VideoDto();

            Optional<VideoMetadata> metadata = videoMetadataRepository.findById(v.getId());
            if (metadata.isPresent()) {
                videoDto.setThumbnail(metadata.get().getThumbnailUrl());
            } else {
                videoDto.setThumbnail("https://media.licdn.com/dms/image/v2/D4E12AQEhA3mEpo1kvA/article-cover_image-shrink_720_1280/article-cover_image-shrink_720_1280/0/1663862064724?e=2147483647&v=beta&t=cGmdXnz2hxBciEnSVGaWEUpPdZfqp30Rczum6TJAkS8");
            }

            videoDto.setStatus(v.getStatus());
            videoDto.setId(v.getId());
            videoDto.setRevisionId(v.getRevisionId());
            videoDto.setChannelName(v.getChannel().getChannelName());
            videoDto.setTitle(v.getTitle());

            videoDtos.add(videoDto);

        });
        return videoDtos;
    }
}