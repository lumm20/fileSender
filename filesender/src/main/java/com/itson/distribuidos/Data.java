package com.itson.distribuidos;


public class Data implements Comparable<Data>{
    public final static String FILE_PART = "filePart";
    public final static String FILE_NAME = "fileName";
    public final static String FILE_PATHS = "filePaths";
    public final static String MISSING_PACKET = "missingPacket";
    private String contentType;
    private String content;
    private int part;
    private int chunks;
    private String clientId;

    public Data(){}

    public Data(int part){
        this.part = part;
    }
    public Data(String contentType, int part, int chunks,String content) {
        this.contentType = contentType;
        this.part = part;
        this.chunks = chunks;
        this.content = content;
    }
    
    public Data(String contentType, int part, String content) {
        this.contentType = contentType;
        this.part = part;
        this.content = content;
    }

    public Data(String contentType, String clientId, String missingParts) {
        this.contentType = contentType;
        this.clientId = clientId;
        this.content = missingParts;
    }
    
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getPart() {
        return part;
    }

    public void setPart(int part) {
        this.part = part;
    }

    public int getchunks() {
        return chunks;
    }

    public void setchunks(int chunks) {
        this.chunks = chunks;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public String toString() {
        return "Data{" + "contentType:"+contentType + ", content:" + content + "}";
    }

    /*
     * los objetos tipo Data ocupan ser comparables para poder ordenar
     * los paquetes al momento de que el cliente los reciba
     */
    @Override
    public int compareTo(Data o) {
        return Integer.compare(this.part, o.part);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + part;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Data other = (Data) obj;
        if (part != other.part)
            return false;
        return true;
    }

    
    
}
