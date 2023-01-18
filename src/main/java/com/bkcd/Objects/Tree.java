package com.bkcd.Objects;

import com.bkcd.ObjectStore;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Set;

public class Tree implements ObjectEntity{
    private HashMap<String, ObjectEntity> childNodes = new HashMap<>();
    private String id = null;

    private final String MODE = "40000";
    private final String TYPE = "tree";


    public void add(String path, Blob blob) {
        int pathSeparator = path.indexOf("/");

        if(pathSeparator == -1) {
            childNodes.put(path, new Blob(blob.getId(), blob.getMode()));
        } else {
            Tree subTree = new Tree();
            subTree.add(path.substring(pathSeparator+1), blob);
            childNodes.put(path.substring(0, pathSeparator), subTree);
        }
    }

    public byte[] getContent() throws IOException {
        String[] childNames = childNodes.keySet().toArray(new String[0]);
        Arrays.sort(childNames);

        ByteArrayOutputStream childStream = new ByteArrayOutputStream();
        String childContent = null;
        String childId = null;

        for(String child : childNames) {
            ObjectEntity childNode = childNodes.get(child);

            childContent = childNode.getMode() + " " + child + "\0";
            childStream.write(childContent.getBytes(StandardCharsets.US_ASCII));

            childId = childNode.getId();
            childStream.write(HexFormat.of().parseHex(childId));
        }

        String treeInfo = "tree " + childStream.size() + "\0";

        ByteArrayOutputStream finalOutputStream = new ByteArrayOutputStream();
        finalOutputStream.write(treeInfo.getBytes(StandardCharsets.US_ASCII));
        childStream.writeTo(finalOutputStream);
        byte[] content = finalOutputStream.toByteArray();

        if(this.id == null) {
            setId(content);
        }
        return content;
    }

    private void setId(byte[] content) {
        this.id = DigestUtils.sha1Hex(content);
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.TYPE;
    }

    public String getMode() {
        return this.MODE;
    }
    public void generate(ObjectStore storage) throws IOException {
        Set<String> childPaths = childNodes.keySet();

        for(String path : childPaths) {
            ObjectEntity childNode = childNodes.get(path);

            if(childNode.getType() == "tree") {
                ((Tree) childNode).generate(storage);
            }
        }
        storage.store(this);
    }
}
