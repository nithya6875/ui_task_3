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
import java.util.concurrent.atomic.AtomicLong;

// Kubernetes client imports
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;

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
        // Execute by creating a short-lived BusyBox pod in the current namespace
        TaskExecution execution = new TaskExecution();
        execution.setStartTime(new Date());

        String namespace = System.getenv().getOrDefault("POD_NAMESPACE", "default");
        String podName = "task-exec-" + System.currentTimeMillis();
        StringBuilder output = new StringBuilder();

        try {
            ApiClient client = null;
            try {
                client = Config.fromCluster();
            } catch (Exception inClusterEx) {
                client = ClientBuilder.defaultClient();
            }
            io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api(client);

            V1Container container = new V1Container()
                .name("runner")
                .image("busybox:1.36")
                .addArgsItem("sh")
                .addArgsItem("-c")
                .addArgsItem(command);

            V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta().name(podName).namespace(namespace))
                .spec(new V1PodSpec()
                    .addContainersItem(container)
                    .restartPolicy("Never"));

            api.createNamespacedPod(namespace, pod, null, null, null, null);

            long start = System.currentTimeMillis();
            long timeoutMs = COMMAND_TIMEOUT_SECONDS * 1000L;

            // Poll pod status until Succeeded/Failed or timeout
            while (true) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    try { api.deleteNamespacedPod(podName, namespace, null, null, 0, null, null, null); } catch (Exception ignore) {}
                    execution.setEndTime(new Date());
                    execution.setOutput("Command execution timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds");
                    throw new RuntimeException("timeout");
                }

                V1Pod current = api.readNamespacedPod(podName, namespace, null);
                V1PodStatus status = current.getStatus();
                String phase = status != null ? status.getPhase() : null;
                if ("Succeeded".equalsIgnoreCase(phase) || "Failed".equalsIgnoreCase(phase)) {
                    break;
                }
                Thread.sleep(500);
            }

            // Fetch logs (merged stdout/stderr is default for Kubernetes logs)
            String logs = api.readNamespacedPodLog(podName, namespace, null, null, null, null, null, null, null, null, null);
            if (logs != null) {
                if (logs.length() > MAX_OUTPUT_SIZE) {
                    output.append(logs, 0, MAX_OUTPUT_SIZE).append("\n[Output truncated due to size limit]");
                } else {
                    output.append(logs);
                }
            }
            execution.setEndTime(new Date());
            execution.setOutput(output.toString());
            return execution;
        } catch (ApiException e) {
            execution.setEndTime(new Date());
            execution.setOutput("Kubernetes API error: " + e.getMessage());
            throw new RuntimeException("k8s_api_error: " + e.getMessage(), e);
        } catch (Exception e) {
            execution.setEndTime(new Date());
            execution.setOutput("Error executing in Kubernetes: " + e.getMessage());
            throw new RuntimeException("k8s_exec_error: " + e.getMessage(), e);
        } finally {
            // Best-effort cleanup; ignore errors if pod already gone
            try {
                ApiClient client;
                try { client = Config.fromCluster(); } catch (Exception inClusterEx) { client = ClientBuilder.defaultClient(); }
                CoreV1Api api = new CoreV1Api(client);
                api.deleteNamespacedPod(podName, namespace, null, null, 0, null, null, null);
            } catch (Exception ignore) {}
        }
    }
}


