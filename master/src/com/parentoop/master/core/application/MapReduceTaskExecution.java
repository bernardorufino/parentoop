package com.parentoop.master.core.application;

import com.google.common.collect.Lists;
import com.parentoop.core.loader.Task;
import com.parentoop.master.core.application.phases.MappingPhase;
import com.parentoop.master.core.application.phases.SetupPhase;
import com.parentoop.master.core.execution.TaskExecution;

import java.nio.file.Path;

public class MapReduceTaskExecution extends TaskExecution<Path> {

    public MapReduceTaskExecution(Path inputPath, Task task, TaskExecutionListener<Path> listener) {
        super(task, Lists.newArrayList(new SetupPhase(), new MappingPhase(inputPath)), listener);
    }
}