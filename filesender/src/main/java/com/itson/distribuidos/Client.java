package com.itson.distribuidos;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import com.google.gson.Gson;

public class Client {
    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private final String outputPath = "filesender/src/main/received/";
    private final Gson gson;
    private Random random;
    private String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private StringBuilder builder;
    private String id;

    public Client(){
        random = new Random();
        gson = new Gson();
        id = generateId();
    }

    /**
     * metodo comun para recibir los paquetes del servidor
     * 
     * @return un objeto Data representando el json recibido en el paquete
     * @throws IOException en caso de que ocurra un error al recibir el paquete
     */
    private Data receive() throws IOException {
        byte[] data = new byte[1024];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        socket.receive(packet);
        // si el paquete llego vacio indica que fue el final de la secuencia
        // de paquetes
        if (packet.getLength() == 0) {
            return null;
        }
        // convierete el json a un objeto Data
        String json = new String(packet.getData(), 0, packet.getLength());
        Data dataReceived = gson.fromJson(json, Data.class);
        return dataReceived;
    }

    private void sendPacket(Data data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, this.port);
        socket.send(packet);
    }

    private String generateId(){
        builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(ALPHANUMERIC.length());
            builder.append(ALPHANUMERIC.charAt(index));
        }
        return builder.toString();
    }

    /**
     * le envia un paquete al servidor para pedir los nombres
     * de los archivos disponibles
     * 
     * @param host ip del servidor
     * @param port puerto del servidor
     * @throws IOException en caso de que ocurra un error al enviar o recibir el
     *                     paquete
     */
    public void askForFiles(String host, int port) throws IOException {
        socket = new DatagramSocket();
        this.port = port;
        address = InetAddress.getByName(host);
        Data data = new Data();
        data.setContentType(Data.FILE_PATHS);
        data.setClientId(this.id);
        sendPacket(data);

        receiveFileNames();
        socket.close();
    }

    /**
     * recibe la respuesta del servidor con los nombres de los archivos disponibles
     * 
     * @throws IOException en caso de que ocurra un error al recibir el paquete
     */
    public void receiveFileNames() throws IOException {
        Data dataReceived = receive();
        System.out.println("json: " + dataReceived);
        System.out.println(dataReceived.getContent());
        sendFileName();
    }

    /**
     * envia el nombre del archivo que se desea recibir
     * 
     * @throws IOException en caso de que ocurra un error al enviar o recibir el
     *                     paquete
     */
    private void sendFileName() throws IOException {
        try (Scanner scanner = new Scanner(System.in);) {
            String fileName = scanner.nextLine();
            Data data = new Data();
            data.setContentType(Data.FILE_NAME);
            data.setContent(fileName);
            data.setClientId(this.id);
            sendPacket(data);
            receiveFile();
        }
    }

    /**
     * primeramente recibe el paquete inicial de la secuencia, el cual
     * contiene la informacion del archivo fragmentado a enviar
     * 
     * @throws IOException en caso de que ocurra un error al recibir los paquetes
     */
    private void receiveFile() throws IOException {
        Data dataReceived = receive();
        System.out.println("json: " + dataReceived);
        System.out.println("json chunks: " + dataReceived.getchunks());
        if (dataReceived.getContentType().equals(Data.FILE_NAME)) {
            String fileName = dataReceived.getContent();
            String fileExtension = fileName.split("\\.")[1];
            int numPackets = dataReceived.getchunks();
            List<DatagramPacket> packets = catchFileParts(numPackets);
            List<Data> datas = convertToData(packets);
            List<Integer> missingPackets = getMissingParts(numPackets, datas);
            if(missingPackets.size() > 0){
                requestMissingParts(missingPackets);
                datas = receiveMissingParts(missingPackets, datas);
            }
            assembleFile(datas, fileName,fileExtension);
            
        }
    }

    private List<Data> receiveMissingParts(List<Integer> missingPackets,List<Data> datas){
        List<DatagramPacket> packets;
        try {
            packets = catchFileParts(missingPackets.size());
            List<Data> missingParts = convertToData(packets);
            if(!missingParts.isEmpty()){
                datas.addAll(missingParts);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return datas;
    }

    private void requestMissingParts(List<Integer> missingPackets) throws IOException {
        System.out.println("Solicitando partes faltantes");
        String str = missingPackets.toString().substring(1, missingPackets.toString().length() - 1);
        str = str.replaceAll(" ", "");
        Data data = new Data(Data.MISSING_PACKET, this.id,str);
        sendPacket(data);
    }

    private void assembleFile(List<Data> packets,String fileName, String fileExtension)throws IOException{
        for (int i = 0; i < packets.size(); i++) {
            if(packets.get(i) == null){
                packets.remove(i);
                break;
            }
        }
        Collections.sort(packets);
        try(FileOutputStream stream = new FileOutputStream(outputPath + fileName)){
            if(fileExtension.equals("txt")){
                saveTxtFile(stream, packets);
            }else{
                for (Data packet : packets) {
                    byte[] decoded = Base64.getDecoder().decode(packet.getContent());
                    stream.write(decoded);
                }
            }
        }
        System.out.println("Archivo guardado con exito");
    }

    private void saveTxtFile(FileOutputStream stream, List<Data> packets) throws IOException {
        try(OutputStreamWriter writer = new OutputStreamWriter(stream,StandardCharsets.UTF_8);
            BufferedWriter buff = new BufferedWriter(writer)){
            for (Data data : packets) {
                byte[] decoded = Base64.getDecoder().decode(data.getContent());
                buff.write(new String(decoded, StandardCharsets.UTF_8));
            }
            buff.flush();   
        }
    }

    private List<DatagramPacket> catchFileParts(int numPackets) throws IOException {
        // List<Data> packets = new ArrayList<>();
        List<DatagramPacket> packets = new ArrayList<>();
        
        byte[] buffer = new byte[1024];
        socket.setSoTimeout(1000);
        DatagramPacket packet;
        try {
            for (int i = 0; i <= numPackets; i++) {
                packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                System.out.println("packet received "+ (i+1));
                packets.add(packet);
                buffer = new byte[1024];
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout reached!!! ");
        }

        return packets;

    }

    private List<Data> convertToData(List<DatagramPacket> packets){
        List<Data> datas = new ArrayList<>();
        String json;
        Data data;
        for(DatagramPacket p : packets){
            json = new String(p.getData(), 0, p.getLength());
            data = gson.fromJson(json, Data.class);
            datas.add(data);
        }
        return datas;
    }

    private List<Integer> getMissingParts(int expectedParts,List<Data> packets){
        if(packets.get(packets.size()-1) == null || packets.size() == expectedParts){
            return new ArrayList<>();
        }
        List<Integer> missingParts = new ArrayList<>();
        List<Integer> receivedParts = new ArrayList<>();

        for (int i = 0; i <packets.size()-1; i++) {
            receivedParts.add(packets.get(i).getPart());
        }

        for (int i = 1; i <= expectedParts; i++) {
            if (!receivedParts.contains(i)) {
                missingParts.add(i);
            }
        }
        return missingParts;
    }
}
