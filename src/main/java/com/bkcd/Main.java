package com.bkcd;

import com.bkcd.Objects.Blob;
import com.bkcd.Objects.Commit;
import com.bkcd.Objects.Tree;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

public class Main {
    private static String repoPath = System.getProperty("user.dir");
    /**
     * Files inside objects directory are stored in compressed format,
     * for that reason we use the ObjecStore class when trying to store
     * blobs, trees & commits
     */
    private static ObjectStore storage = new ObjectStore(Path.of(repoPath, ".git/objects"));
    private static Refs refs = new Refs(repoPath +"/.git");
//    private static List<Path> IGNORE_FILES = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        String command = args[0];
        switch (command) {
            case "init" -> {
                initializeRepo(args);
            }

            case "add" -> {
                //TreeMap will take care of both sorting & duplicate keys
                HashMap<String,String> indexEntries = new HashMap<>();

                for(int i = 1; i < args.length; i++) {
                    Path path = Path.of(args[i]).toAbsolutePath().normalize();

                    //For now only .git folder is part of ignored files. Extend this feature later.
                    try (Stream<Path> dirContent = Files.walk(path)) {
                        dirContent.filter(filePath -> !filePath.startsWith(Path.of(repoPath,".git")))
                                .filter(Files::isRegularFile)
                                .forEach(filePath -> {
                                    String blobId = saveBlob(filePath);
                                    Path relativeFilePath = Path.of(repoPath).relativize(filePath);
                                    indexEntries.put(relativeFilePath.toString(), blobId);
                                });
                    }
                }

                Index index = new Index();
                index.addFiles(indexEntries);
            }

            // To add message for a commit there is no need for -m flag
            case "commit" -> {
                HashMap<String, Blob> indexData = new Index().fetchIndexData();
                Tree tree = new Tree();

                indexData.forEach((path, blob) -> {
                    tree.add(path, blob);
                });

                tree.generate(storage);

                String commitMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String parentId = "root-commit";

                try {
                    //If HEAD file doesn't point to a ref file then we are at root commit
                    parentId = Files.readString(Path.of(repoPath, ".git", refs.retrieveHead()));
                } catch (IOException e) {}

                Commit commit = new Commit(parentId ,tree.getId(), commitMessage);
                storage.store(commit);
                refs.updateRef(commit.getId());
            }

            case "branch" -> {
                String branchName = args[1];
                refs.createBranch(branchName);
            }

            case "checkout" -> {
                String branchName = args[1];
                refs.updateHead("refs/heads/" + args[1]);
            }

            // This diff implementation accepts the file path & compares the workspace file to the file present in staging area
            case "diff" -> {
                HashMap<String, Blob> indexData = new Index().fetchIndexData();
                String blobId = indexData.get(args[1]).getId();
                Path blobPath = Path.of(repoPath,
                                    ".git/objects",
                                    blobId.substring(0,2),
                                    blobId.substring(2));
                byte[] blobData = new InflaterInputStream(new FileInputStream(blobPath.toFile())).readAllBytes();

                // Data starts after null
                int nullIndex = 0;
                for(int i = 0; i < blobData.length; i++) {
                    if(blobData[i] == 0x00) {
                        nullIndex = i;
                        break;
                    }
                }
                System.out.println("nullIndex: " + nullIndex);
                String[] blobExtract = new String(Arrays.copyOfRange(blobData, nullIndex+1, blobData.length))
                                            .split("\n");


                String[] workspaceData = Files.readAllLines(Path.of(args[1]))
                                            .toArray(new String[0]);

                generateDiff(blobExtract, workspaceData);
            }

            // Lists the files currently being tracked by delta
            case "tracked" -> {
                HashMap<String, Blob> indexData = new Index().fetchIndexData();
                System.out.println(indexData.keySet());
            }
        }
    }

    public static void generateDiff(String[] oldData, String[] newData) {
        String[] text1 = newData;
        String[] text2 = oldData;

        // Build a grid to identify the largest common set of lines between the two text sources
        int[][] dpGrid = new int[text1.length + 1][text2.length + 1];

        for (int col = text2.length - 1; col >= 0; col--) {
            for (int row = text1.length - 1; row >= 0; row--) {
                // If the corresponding lines for this cell are the same...
                if (text1[row].equals(text2[col])) {
                    dpGrid[row][col] = 1 + dpGrid[row + 1][col + 1];
                } else {
                    dpGrid[row][col] = Math.max(dpGrid[row + 1][col], dpGrid[row][col + 1]);
                }
            }
        }

        // Use the grid constructed above to extract the diff
        StringBuilder diff = new StringBuilder("");
        int i = text1.length - 1;
        int j = text2.length - 1;

        /**
         * For deletion to take priority over insertion, we traverse the grid from bottom.
         * And in case dpGrid[i-1][j] & dpGrid[i][j-1] have same value then we prefer adding from text2
         * rather than removing from text1.
         * To prioritize addition before removal, we will traverse from the top & prefer the lower value
         */

        while(!(i < 0 && j < 0)) {
            if(i*j <= 0) {
                /**
                 * First 2 cases are when we run out of either data source, all we have to do then is insert the remaining lines from other source
                 * Last three conditions cover the cases for 1st row or 1st column traversal
                 */
                if(i < 0) {
                    diff.insert(0, "\n- " + text2[j]);
                    j--;
                } else if(j < 0) {
                    diff.insert(0, "\n+ " + text1[i]);
                    i--;
                } else if(text1[i].equals(text2[j])) {
                    diff.insert(0, "\n  " + text1[i]);
                    i--;
                    j--;
                } else if(i == 0) {
                    diff.insert(0, "\n- " + text2[j]);
                    j--;
                } else if(j == 0) {
                    diff.insert(0, "\n+ " + text1[i]);
                    i--;
                }
            } else if(text1[i].equals(text2[j])) {
                diff.insert(0, "\n  " + text1[i]);
                i--;
                j--;
            } else if (dpGrid[i-1][j] >= dpGrid[i][j-1]) {
                diff.insert(0, "\n+ " + text1[i]);
                i--;
            } else {
                diff.insert(0, "\n- " + text2[j]);
                j--;
            }
        }

        System.out.println(diff);
    }

    private static void initializeRepo(String[] args) {
        if (args.length > 1) {
            repoPath = args[1];
        }
        Path absolutePath = Path.of(repoPath).toAbsolutePath();
        Path gitDirectory = Path.of(absolutePath.toString(), ".git");
        Path objects = Path.of(gitDirectory.toString(), "objects");
        Path refs = Path.of(gitDirectory.toString(), "refs/heads");

        try {
            Files.createDirectories(objects);
            Files.createDirectories(refs);
            Files.writeString(Path.of(repoPath, ".git/HEAD"), "refs/heads/main");
        } catch (IOException e) {
            System.err.println("Unable to initialize .git directory");
            System.exit(1);
        }
        System.out.println("Delta repo initialized in: " + gitDirectory);
    }

    private static String saveBlob(Path filePath) {
        String fileData = null;
        try {
            fileData = Files.readString(filePath, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            System.err.println("Unable to read file: " + filePath);
        }

        Blob blob = new Blob(fileData);
        try {
            storage.store(blob);
        } catch (IOException e) {
            System.err.println("Unable to write blob file for: " + filePath.toString());
            System.err.println(e);
            System.exit(1);
        }
        return blob.getId();
    }
}
