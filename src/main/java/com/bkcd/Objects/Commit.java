package com.bkcd.Objects;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Commit implements ObjectEntity {
    private String id = null;
    private final String message;
    private final String treeId;
    private final String parentId;
    private final String type = "commit";

    public Commit(String parentId, String treeId, String message) {
        this.message = message;
        this.treeId = treeId;
        this.parentId = parentId;
    }

    public byte[] getContent() {
        String parentInfo = (parentId == null) ? "" : ("parent " + parentId + "\n");
        String info = ("tree " + this.treeId) + "\n"
                + parentInfo
                + authorInfo() + "\n"
                + message;
        byte[] content = ("commit " + info.getBytes(StandardCharsets.US_ASCII).length + "\0" + info).getBytes(StandardCharsets.US_ASCII);
        if(this.id == null) {
            setId(content);
        }
        return content;
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.type;
    }

    public String getMode() {
        return null;
    }

    private String authorInfo() {
        String name = System.getenv("DELTA_AUTHOR_NAME");
        String email = System.getenv("DELTA_AUTHOR_EMAIL");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E MMM d hh:mm:ss u XXX");
        String time = ZonedDateTime.now().format(formatter);
        return "Author: " + name + " <" + email + ">\n" + "Date:   " + time;
    }

    private void setId(byte[] content) {
        this.id = DigestUtils.sha1Hex(content);
    }
}
