package com.inspire17.ythelper.document;

import com.inspire17.ythelper.dto.CommentDataDto;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "video_comments")
@Getter
@Setter
public class VideoComment {
    @Id
    private String id;

    private Long videoId; // Reference to SQL VideoEntity

    private Long userId; // Who commented

    private List<CommentDataDto> comments; // Updated to support text & audio

    private LocalDateTime createdAt = LocalDateTime.now();
}