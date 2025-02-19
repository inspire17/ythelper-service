package com.inspire17.ythelper.repository;

import com.inspire17.ythelper.document.AdminInstructions;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InstructionRepository extends MongoRepository<AdminInstructions, String> {
}
