package com.example.taskapi.service;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskExecution;
import com.example.taskapi.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);
    private static final int COMMAND_TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1MB
    private static final int MAX_COMMAND_LENGTH = 1024;

    // Dangerous command tokens to reject (case-insensitive)
    private static final String[] DANGEROUS_TOKENS = new String[]{
        "rm", "sudo", "reboot", "shutdown", "mv", "dd", ": >", ">", ">>",
        "|", ";", "&", "&&", "||", "`", "$(", "wget", "curl", "nc",
        "netcat", "ncat", "chmod", "chown", "mkfs", "dd if=", "dd of="
    };

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }

    public List<Task> searchTasksByName(String name) {
        return taskRepository.findByNameContainingIgnoreCase(name);
    }

    public Task saveTask(Task task) {
        validateCommand(task.getCommand());
        logger.info("Creating/updating task: {}", task.getName());
        return taskRepository.save(task);
    }

    public boolean deleteTask(String id) {
        if (taskRepository.existsById(id)) {
            logger.info("Deleting task with id: {}", id);
            taskRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public TaskExecution runTask(String id) {
        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            throw new IllegalStateException("Task not found with id: " + id);
        }

        Task task = taskOpt.get();
        logger.info("Executing task: {} with command: {}", task.getName(), task.getCommand());

        TaskExecution execution = executeCommand(task.getCommand());
        task.getTaskExecutions().add(execution);
        taskRepository.save(task);

        return execution;
    }

    private void validateCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        if (command.length() > MAX_COMMAND_LENGTH) {
            throw new IllegalArgumentException("Command exceeds maximum length of " + MAX_COMMAND_LENGTH + " characters");
        }

        if (command.contains("\n") || command.contains("\r")) {
            throw new IllegalArgumentException("Command cannot contain newline characters");
        }

        String lowerCommand = command.toLowerCase();
        for (String token : DANGEROUS_TOKENS) {
            if (lowerCommand.contains(token.toLowerCase())) {
                throw new IllegalArgumentException("Command contains forbidden token: " + token);
            }
        }
    }

    private TaskExecution executeCommand(String command) {
        TaskExecution execution = new TaskExecution();
        execution.setStartTime(new Date());

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.redirectErrorStream(true); // merge stderr into stdout

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }

        try {
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() + 1 > MAX_OUTPUT_SIZE) {
                        output.append("\n[Output truncated due to size limit]");
                        break;
                    }
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                execution.setEndTime(new Date());
                execution.setOutput("Command execution timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds");
                throw new RuntimeException("timeout");
            }

            execution.setEndTime(new Date());
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("[Process exited with code: ").append(exitCode).append("]");
            }
            execution.setOutput(output.toString());
            return execution;
        } catch (IOException e) {
            execution.setEndTime(new Date());
            execution.setOutput("Error executing command: " + e.getMessage());
            throw new RuntimeException("execution_error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            execution.setEndTime(new Date());
            execution.setOutput("Interrupted while executing command");
            throw new RuntimeException("execution_interrupted", e);
        }
    }
}


