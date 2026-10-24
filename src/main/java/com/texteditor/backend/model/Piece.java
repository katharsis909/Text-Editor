package com.texteditor.backend.model;

/**
 * Piece descriptor storing what part of which buffer to read and for how long.
 * Does not store mutable absolute coordinates; only buffer type, start offset, and length.
 */
public class Piece {
    private BufferType buffer;
    private int start;
    private int length;

    public Piece() {}

    public Piece(BufferType buffer, int start, int length) {
        this.buffer = buffer;
        this.start = start;
        this.length = length;
    }

    public BufferType getBuffer() {
        return buffer;
    }

    public void setBuffer(BufferType buffer) {
        this.buffer = buffer;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Piece piece = (Piece) o;
        return start == piece.start && length == piece.length && buffer == piece.buffer;
    }

    @Override
    public int hashCode() {
        int result = buffer != null ? buffer.hashCode() : 0;
        result = 31 * result + start;
        result = 31 * result + length;
        return result;
    }

    @Override
    public String toString() {
        return "Piece{" +
                "buffer=" + buffer +
                ", start=" + start +
                ", length=" + length +
                '}';
    }
}
