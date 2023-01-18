package com.bkcd;

import com.bkcd.Objects.Blob;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class Index {
    private Path indexPath = Path.of(System.getProperty("user.dir"), ".git/index");
    private HashMap<String,String> entryIds = new HashMap<>();
    private HashMap<String, byte[]> existingEntryData = new HashMap<>();

    public void addFiles(HashMap<String, String> newEntryIds) throws IOException {
        if(newEntryIds.size() == 0) return;

        if(!Files.exists(indexPath)) {
            Files.createFile(indexPath);
        } else {
            loadIndex();
        }

        entryIds.putAll(newEntryIds);    //insert newly added files to index entries

        List<String> sortedEntryPaths = new ArrayList<>();
        sortedEntryPaths.addAll(entryIds.keySet());
        Collections.sort(sortedEntryPaths);

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.write(getHeader(entryIds.size()));

        sortedEntryPaths.forEach( path -> {
            String blobId = entryIds.get(path);
            try {
                System.out.println(path + ": " + blobId);
                /**
                 * This condition would hold true for unchanged index entries.
                 * For such cases we can avoid re-computation of entry data.
                 */
                if(blobId == null) {
                    content.write(existingEntryData.get(path));
                } else {
                    content.write(getEntryData(Path.of(path), blobId));
                }
            } catch (IOException e) {
                /**
                 * Have to do this as we are not allowed to throw checked exceptions because the accept(T t, U u)
                 * method in the java.util.function.BiConsumer<T, U> interface doesn't declare any exceptions
                 */
                throw new RuntimeException(e);
            }
        });

        byte[] byteContent = content.toByteArray();
        String indexId = DigestUtils.sha1Hex(byteContent);      //Generate SHA-1 hash for contents of index file

        OutputStream out = new BufferedOutputStream(new FileOutputStream(indexPath.toFile()));
        out.write(byteContent);
        out.write(HexFormat.of().parseHex(indexId));
        out.close();
    }

    private byte[] getEntryData(Path path, String blobId) throws IOException {
        Map<String, Object> meta = Files.readAttributes(path, "unix:*");

        byte[] ctime_s = ByteBuffer.allocate(4)
                .putInt((int) (((FileTime)meta.get("ctime")).toMillis()/1000))
                .array();
        byte[] ctime_n = ByteBuffer.allocate(4)
                .putInt(((FileTime)meta.get("ctime")).toInstant().getNano())
                .array();

        byte[] mtime_s = ByteBuffer.allocate(4)
                .putInt((int) (((FileTime)meta.get("lastModifiedTime")).toMillis()/1000))
                .array();
        byte[] mtime_n = ByteBuffer.allocate(4)
                .putInt(((FileTime)meta.get("lastModifiedTime")).toInstant().getNano())
                .array();

        //Casting to int direclty only works when it is known for sure that the Object is of type int
        byte[] dev = ByteBuffer.allocate(4)
                .putInt(((Number) meta.get("dev")).intValue())
                .array();

        byte[] inode = ByteBuffer.allocate(4)
                .putInt(((Number) meta.get("ino")).intValue())
                .array();

        //In git mode can only take two values depending on whether the file is executable or not
        byte[] mode =  ByteBuffer.allocate(4)
                .putInt(Files.isExecutable(path) ? 0100755 : 0100644)
                .array();

        byte[] uid = ByteBuffer.allocate(4)
                .putInt(((Number) meta.get("uid")).intValue())
                .array();

        byte[] gid = ByteBuffer.allocate(4)
                .putInt(((Number) meta.get("gid")).intValue())
                .array();

        byte[] size = ByteBuffer.allocate(4)
                .putInt(((Number) meta.get("size")).intValue())
                .array();

        /**
         * For saving index entries we use file path relative to the repo path,
         * this allows us to reconstruct the index entries for future incremental
         * changes to the index file
         */
        byte[] fileName = path.toString()
                .getBytes(StandardCharsets.US_ASCII);

        //If filename length exceeds 2 byte range then we just use 0xffff instead
        byte[] nameLength = ByteBuffer.allocate(2)
                .putShort((short) (Math.min(fileName.length, 0xffff)))
                .array();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ctime_s);
        out.write(ctime_n);
        out.write(mtime_s);
        out.write(mtime_n);
        out.write(dev);
        out.write(inode);
        out.write(mode);
        out.write(uid);
        out.write(gid);
        out.write(size);
        out.write(HexFormat.of().parseHex(blobId));
        out.write(nameLength);
        out.write(fileName);

        /**
         * Each entry is to be padded with null bytes until its length is a multiple of 8 bytes.
         * Also, there needs to be at lease one null byte to terminate the entry’s path, so if
         * the entry's length is already a multiple of 8, then 8 null bytes should be appended.
         */
        int paddingLength = (out.size() % 8 == 0) ? 8 : 8*((int) Math.ceil(out.size()/8.0)) - out.size();
        out.write(new byte[paddingLength]);

        return out.toByteArray();
    }

    private byte[] getHeader(int size) throws IOException {
        byte[] signature = "DIRC".getBytes();
        byte[] version = ByteBuffer.allocate(4).putInt(2).array();
        byte[] entryCount = ByteBuffer.allocate(4).putInt(size).array();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(signature);
        out.write(version);
        out.write(entryCount);

        return out.toByteArray();
    }

    public void loadIndex() throws IOException {
        byte[] indexData = Files.readAllBytes(indexPath);

        if(!validateChecksum(indexData)) {
            System.out.println("Index file checksum validation failed");
            System.exit(1);
        }

        int entryCount = ByteBuffer.wrap(
                Arrays.copyOfRange(indexData, 8, 12))
                .getInt();

        //Entry data is present after first 12 bytes of header
        int entryStart = 12;

        for(int i = 0; i < entryCount; i++) {
            int entryEnd = entryStart + 64 - 1;

            /**
             * Due to null byte paddding done during index creation,
             * entry lenths are always multiple of 8
             */
            while(indexData[entryEnd] != 0) entryEnd += 8;

            insertEntry(Arrays.copyOfRange(indexData, entryStart, entryEnd+1));
            entryStart = entryEnd+1;
        }
    }

    // Check for index file data corruption before loading the data
    private boolean validateChecksum(byte[] indexData) {
        int contentSize = indexData.length - 20;
        byte[] content = Arrays.copyOfRange(indexData, 0 , contentSize);
        byte[] checkSum = Arrays.copyOfRange(indexData, contentSize, indexData.length);

        String oldSHA = HexFormat.of().formatHex(checkSum);
        String newSHA = DigestUtils.sha1Hex(content);

        return newSHA.equals(oldSHA);
    }

    private void insertEntry(byte[] entryData) {
        String path = new String(Arrays.copyOfRange(entryData, 62, entryData.length));
        path = path.replace("\0", "");

        entryIds.put(path, null);
        existingEntryData.put(path, entryData);
    }

    // Returns existing entries in index file & their corresponding blobIds
    public HashMap<String, Blob> fetchIndexData() throws IOException {
        loadIndex();
        HashMap<String, Blob> trackedEntries = new HashMap<>();

        //Extract blobId from index entry data
        existingEntryData.forEach((path, data) -> {
            Blob blob = new Blob(HexFormat.of().formatHex(Arrays.copyOfRange(data, 40, 60)), Files.isExecutable(Path.of(path)) ? "100755" : "100644");
            trackedEntries.put(path, blob);
        });

        return trackedEntries;
    }
}
