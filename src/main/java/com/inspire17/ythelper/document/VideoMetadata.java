package com.inspire17.ythelper.document;

import com.inspire17.ythelper.dto.InstructionDto;
import com.inspire17.ythelper.helper.annotations.ToJsonString;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "video_metadata")
@Getter
@Setter
@ToJsonString
public class VideoMetadata {

    public String storageType;
    @Id
    private String id;
    private String videoId;
    private String description;
    private String title;
    private String storageEnv;
    private String rawVideoUrl;
    private String thumbnailUrl;
    private List<InstructionDto> editorInstructions;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String url;
}
