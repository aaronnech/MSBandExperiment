package com.psych.aaron.heartratereader;

/**
 * Represents a data packet that is destined for the central server
 */
public class Packet {
    public String type;
    public String data;
    public String bandID;
    public long timeStamp;

    @Override
    public String toString() {
        return bandID + "```///" + type + "```///" + data + "```///" + timeStamp;
    }
}
