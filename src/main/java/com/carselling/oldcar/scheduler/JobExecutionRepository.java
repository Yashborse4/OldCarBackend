package com.carselling.oldcar.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    List<JobExecution> findByJobNameOrderByStartTimeDesc(String jobName);

    // Find latest execution for a job
    JobExecution findTopByJobNameOrderByStartTimeDesc(String jobName);

    // Find latest successful execution for incremental indexing
    JobExecution findTopByJobNameAndStatusOrderByStartTimeDesc(String jobName, JobExecution.JobStatus status);
}
