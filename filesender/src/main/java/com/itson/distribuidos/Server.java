package com.itson.distribuidos;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.gson.Gson;

public class Server {
    private static DatagramSocket socket;
    private static ExecutorService executor;
    private static final List<ClientHandler> handlers = new ArrayList<>();
    private Gson gson;
    
    public Server() {
        executor = Executors.newCachedThreadPool();
        gson = new Gson();
    }

    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.control();
            server.start(8080);
        } catch (Exception e) {
            socket.close();
            executor.shutdown();
            e.printStackTrace();
        }
    }

    /**
     * este metodo inicia un hilo para poder apagar el servidor
     * al escribir 'exit' en la consola
     */
    private void control() {
        executor.execute(() -> {
            Scanner scanner = new Scanner(System.in);
            boolean running = true;
            do {
                System.out.println("Type 'exit' to stop the server");
                String line = scanner.nextLine();
                if (line.equals("exit")) {
                    System.out.println("Server stopped");
                    running = false;
                    scanner.close();
                    close();
                }
            } while (running);
        });
    }

    private void close() {
        socket.close();
        executor.shutdown();
    }

    public void start(int port) {
        try {
            socket = new DatagramSocket(port);
            System.out.println("Server started on port " + port);
            byte[] bytes = new byte[1024];
            DatagramPacket packet;
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                packet = new DatagramPacket(bytes, bytes.length);
                socket.receive(packet);
                System.out.println("---------------------------");
                System.out.println("packet received");
                System.out.println("---------------------------");
                // executor.execute(managePacket(packet));
                Data data = readPacket(packet);
                
                Callable<ClientHandler> task = manageP(data, packet);
                Future<ClientHandler> future = executor.submit(task);
                try {
                    ClientHandler handler = future.get();
                    System.out.println("handler: " + handler.getId());
                    if(!handlers.contains(handler)){
                        handlers.add(handler);
                    }
                    if(data.getContentType().equals(Data.MISSING_PACKET)){
                        handlers.remove(handler);
                    }else{
                        handlers.set(handlers.size()-1, handler);
                    }
                } catch (Exception e) {
                    System.out.println("error: "+e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private Callable<ClientHandler> manageP(Data data, DatagramPacket packet) throws IOException {
        Callable<ClientHandler> task = ()->{
            ClientHandler h = null;
            try {
                if(data.getContentType().equals(Data.MISSING_PACKET)){
                    System.out.println("missing packet");
                    System.out.println("empty handlers?: "+handlers.isEmpty());
                    for (ClientHandler clientHandler : handlers) {
                        System.out.println("id: "+clientHandler.getId());
                        if(clientHandler.getId().equals(data.getClientId())){
                            h = clientHandler;
                            System.out.println("handler: " + clientHandler.getId() +", "+clientHandler.getDatas());
                            h.resendPackets(data);
                            break;
                        } 
                    }
                }else{
                    ClientHandler handler = new ClientHandler(socket, packet, data.getClientId());
                    h = handler.start();
                    System.out.println("h: "+h.getId());
                    for (Data d : h.getDatas()) {
                        System.out.println("d: "+d.getPart());
                    }
                }
            } catch (Exception e) {
                System.out.println("Error");
                e.printStackTrace();
            }
            return h;
        };
        return task;
    }
    private Runnable managePacket(DatagramPacket packet) throws IOException{
        return ()->{
            try {
                Data data = readPacket(packet);
                if(data.getContentType().equals(Data.MISSING_PACKET)){
                    for (ClientHandler clientHandler : handlers) {
                        if(clientHandler.getId().equals(data.getClientId())){
                            System.out.println("handler: " + clientHandler.getId() +", "+clientHandler.getDatas());
                            clientHandler.resendPackets(data);
                            break;
                        } 
                    }
                }else{
                    ClientHandler handler = new ClientHandler(socket, packet, data.getClientId());
                    handlers.add(handler);
                    int index = handlers.size()-1;
                    Callable<ClientHandler> task = () -> handlers.get(index).start();
                    Future<ClientHandler> future = executor.submit(task);
                    try {
                        ClientHandler newHandler = future.get();
                        System.out.println("datas in handler");
                        for (Data x : handler.getDatas()) {
                            System.out.println(x.getClientId());
                            System.out.println(x.getPart());
                        }
                        handlers.set(index, newHandler);
                        System.out.println("handlers "+handlers.size());
                    } catch (Exception e) {
                        System.out.println("error: "+e.getMessage());
                    }
                    System.out.println("executed request");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    private Data readPacket(DatagramPacket packet) throws IOException{
        byte[] data = packet.getData();
        String json = new String(data, 0, packet.getLength());
        Data dataReceived = gson.fromJson(json, Data.class);
        System.out.println("json: " + dataReceived);
        System.out.println(dataReceived.getContent());
        return dataReceived;
    }
}
