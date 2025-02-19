package com.inspire17.ythelper.document;

import com.inspire17.ythelper.dto.InstructionType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "admin_instructions")
@Getter
@Setter
public class AdminInstructions {
    @Id
    private String id;
    private String videoId;
    private InstructionType instructionType;
    private String content;
}
