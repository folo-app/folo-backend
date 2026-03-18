package com.folo.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportResultRepository extends JpaRepository<ImportResult, Long> {

    List<ImportResult> findByImportJobIdOrderByIdAsc(Long importJobId);
}
