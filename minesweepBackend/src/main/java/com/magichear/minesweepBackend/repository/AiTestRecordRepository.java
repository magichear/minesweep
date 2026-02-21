package com.magichear.minesweepBackend.repository;

import com.magichear.minesweepBackend.entity.AiTestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiTestRecordRepository extends JpaRepository<AiTestRecord, Long> {

    List<AiTestRecord> findAllByOrderByCreatedAtDesc();
}
