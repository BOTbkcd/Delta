package com.bkcd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Refs {
    private final String gitFolder;

    public Refs(String folder) {
        this.gitFolder = folder;
    }

    public void createBranch(String name) throws IOException {
        Path currentHead = Path.of(gitFolder, retrieveHead());
        String id = Files.exists(currentHead) ? Files.readString(currentHead) : "";
        try {
            Files.writeString(Path.of(gitFolder, "refs/heads", name), id);
        } catch (IOException e) {
            System.out.println("Unable to create new branch");
        }
    }

    public void updateRef(String id) throws IOException {
        Path refPath = Path.of(gitFolder, retrieveHead());
        Files.writeString(refPath, id);
    }

    public void updateHead(String refPath) {
        if(Files.exists(Path.of(refPath))) {
            try {
                Files.writeString(Path.of(gitFolder, "HEAD"), refPath);
            } catch (IOException e) {
                System.out.println("Unable to update HEAD");
            }
        } else {
            System.out.println("Branch with specified name does not exist!");
        }
    }

    public String retrieveHead() {
        try {
            return Files.readString(Path.of(gitFolder, "HEAD"));
        } catch (IOException e) {
            System.out.println("Unable to retrieve HEAD");
            return null;
        }
    }
}
