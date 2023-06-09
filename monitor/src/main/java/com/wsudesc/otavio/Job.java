package com.wsudesc.otavio;

import java.nio.file.Path;

public class Job {
    private JobType type;
    private Path filepath;

    public Job(JobType type, Path filepath) {
        this.type = type;
        this.filepath = filepath;
    }

    public JobType getType() {
        return this.type;
    }

    public Path getFilepath() {
        return this.filepath;
    }
}
