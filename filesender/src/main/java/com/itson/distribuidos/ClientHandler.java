package com.itson.distribuidos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

public class ClientHandler implements Runnable {
    private DatagramSocket socket;
    private DatagramPacket clientPacket;
    private InetAddress address;
    private int port;
    private Gson gson;
    private String id;
    private final List<Data> dataList;

    public ClientHandler(DatagramSocket socket, DatagramPacket packet, String id) {
        this.socket = socket;
        this.clientPacket = packet;
        this.address = packet.getAddress();
        this.port = packet.getPort();
        this.gson = new Gson();
        dataList = new ArrayList<>();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public ClientHandler start(){
        byte[] packet = clientPacket.getData();
        String json = new String(packet, 0, clientPacket.getLength());
        Data data = gson.fromJson(json, Data.class);
        try {
            sendResponse(data);
            System.out.println("Response sent to client");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    /*
     * este metodo se ejecuta por cada paquete recibido en el servidor.
     * se encarga de obtener el json del paquete y le manda una respuesta
     * al cliente a partir del tipo de contenido indicado por el
     * campo 'contentType' en el json.
     */
    @Override
    public void run() {
        byte[] packet = clientPacket.getData();
        String json = new String(packet, 0, clientPacket.getLength());
        Data data = gson.fromJson(json, Data.class);
        try {
            sendResponse(data);
            System.out.println("Response sent to client: " + data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Data> getDatas(){
        return dataList;
    }
    private void sendResponse(Data data) throws IOException {
        if (data.getContentType() != null && !data.getContentType().isBlank()) {
            if (data.getContentType().equals(Data.FILE_PATHS)) {
                showFilePaths();
            } else if (data.getContentType().equals(Data.FILE_NAME)) {
                manageRequest(data);
            }
        }
    }

    private void manageRequest(Data data) throws IOException {
        boolean flag = false;
        URL resource = null;
        String fileName, extension = null;
        if (data.getContent() != null && !data.getContent().isBlank()) {
            if (data.getContent().matches("cat[1-6]")) {
                fileName = data.getContent() + ".jpg";
                resource = this.getClass().getClassLoader().getResource(fileName);
                extension = "jpg";
                flag = true;
            } else if (data.getContent().equals("saludo")) {
                fileName = data.getContent() + ".txt";
                resource = this.getClass().getClassLoader().getResource(fileName);
                extension = "txt";
                flag = true;
            }
        }

        if (flag) {
            sendFile(resource, extension);
        } else {
            showFilePaths();
        }
    }

    /**
     * le envia los nombres de los archivos disponibles para enviarse
     */
    private void showFilePaths() throws IOException {
        String files = "Escoge el nombre del archivo que deseas enviar:"
                + "1)cat1, 2)cat2, 3)cat3, 4)cat4, 5)cat5, 6)cat6, 7)saludo";
        Data data = new Data();
        data.setContentType(Data.FILE_PATHS);
        data.setContent(files);
        String json = gson.toJson(data);
        byte[] buffer = json.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }

    /**
     * Crea el archivo a partir de la ruta especificada y
     * le envia el archivo al cliente
     * 
     * @param filePath Ruta del archivo indicado por el cliente
     * @throws IOException en caso de que ocurra un error al enviar los paquetes
     */
    private void sendFile(URL filePath, String extension) throws IOException {
        File file = new File(filePath.getFile());
        try (FileInputStream stream = new FileInputStream(file)) {
            // obtener la cantidad de fragmentos del archivo
            long size = file.length();
            int parts = 0;
            if (size > 0) {
                parts = (int) Math.ceil((double) size / 600);
                // enviar json con metadatos del archivo
                sendMetadata(file.getName(), parts);
                // crear y enviar fragmentos del archivo
                List<String> fragments = splitFile(stream, extension);
                sendHalfPackets(fragments);
            }
        } catch (IOException e) {
            throw e;
        }

    }

    private void sendHalfPackets(List<String> fragments) throws IOException {
        int half = fragments.size() / 2;
        List<String> halfList = fragments.subList(0, half);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        halfList.forEach(part -> {
            scheduler.schedule(() -> {
                System.out.println("Sending packet");
                byte[] bytes = part.getBytes();
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, 200, TimeUnit.MILLISECONDS);
        });

        scheduler.schedule(() -> {
            System.out.println("Shutting down scheduler");
            scheduler.shutdown();
            try {
                if (scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void sendPackets(List<String> partsList)throws IOException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        partsList.forEach(part -> {
            scheduler.schedule(() -> {
                System.out.println("Sending packet");
                byte[] bytes = part.getBytes();
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, 200, TimeUnit.MILLISECONDS);

        });
        scheduler.schedule(()->{
            System.out.println("Sending last packet");
                DatagramPacket packet = new DatagramPacket(new byte[0], 0, address, port);
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }, 200, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> {
            System.out.println("Shutting down scheduler");
            scheduler.shutdown();
            try {
                if (scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 3, TimeUnit.SECONDS);
        
        
    }

    /**
     * Divide el archivo en fragmentos de 600 bytes y los codifica en base64,
     * creando una lista con los strings representando el json que contendra cada
     * paquete a enviar.
     * @param stream El stream para leer el archivo
     * @param extension La extension del archivo
     * @return Una lista con los strings representando cada json a enviar
     * @throws IOException en caso de que ocurra un error al dividir el archivo
     */
    private List<String> splitFile(FileInputStream stream, String extension)throws IOException {
        List<String> partsList = new ArrayList<>();
        byte[] bytes = new byte[600];
        int readBytes = 0;
        Data data = null;
        String json = null;

        if (extension.equals("txt")) {
            partsList = readTextFile(stream, partsList, bytes, 0, data, json);
        } else {
            partsList = readFile(stream, bytes, readBytes, 0, data, partsList, json);
        }
        return partsList;
    }

    /**
     * Lee el archivo y lo fragmenta en partes de 600 bytes, codificandolas en base64,
     * luego agrega a una lista el json con el fragmento. 
     * @param stream Stream para leer el archivo
     * @param buffer arreglo de bytes donde se almacenara el contenido del archivo
     * @param readBytes variable que va a guardar la cantidad de bytes leidos en cada iteracion
     * @param parts contador que va a indicar el numero en la secuencia de los fragmentos
     * @param data objeto Data que va a contener la informacion del fragmento del archivo
     * @param partsList lista de json que se va a enviar en los paquetes
     * @param json string que va a representar el objeto Data convertido en json
     * @return la lista de json con los fragmentos del archivo
     * @throws IOException en caso de que ocurra un error al leer el archivo
     */
    private List<String> readFile(FileInputStream stream, byte[] buffer, int readBytes, 
    int parts, Data data, List<String> partsList, String json) throws IOException {
        // leer cada 600 bytes del archivo
        while ((readBytes = stream.read(buffer)) != -1) {
            // codificar archivo en base64
            String encodedFile = encode(buffer);
            parts++;
            // crea un objeto Data para el fragmento del archivo
            data = new Data(Data.FILE_PART, parts, encodedFile);
            dataList.add(data);
            // convierte el objeto a json
            json = gson.toJson(data);
            partsList.add(json);
        }
        System.out.println("DataList: " + dataList.size());
        return partsList;
    }

    /**
     * Lee el archivo y lo fragmenta en partes de 600 bytes, codificandolas en base64,
     * luego agrega a una lista el json con el fragmento. 
     * @param stream Stream para leer el archivo
     * @param buffer arreglo de bytes donde se almacenara el contenido del archivo
     * @param readBytes variable que va a guardar la cantidad de bytes leidos en cada iteracion
     * @param parts contador que va a indicar el numero en la secuencia de los fragmentos
     * @param data objeto Data que va a contener la informacion del fragmento del archivo
     * @param partsList lista de json que se va a enviar en los paquetes
     * @param json string que va a representar el objeto Data convertido en json
     * @return la lista de json con los fragmentos del archivo
     * @throws IOException en caso de que ocurra un error al leer el archivo
     */ 
    private List<String> readTextFile(FileInputStream stream, List<String> partsList,
     byte[] bytes, int parts, Data data, String json) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(reader);
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[100];
        int bytesLeidos;
        /*
         * se leen cada 100 caracteres del archivo.
         * se escoge 100 caracteres porque, dependiendo del contenido del texto (por ejemplo, 
         * si contiene caracteres especiales o emojis), el tamaño de los bytes que ocupa
         * cada caracter varia. Por lo tanto, se escoge un tamaño fijo para tener un buen margen
         * para evitar que la lectura de los caracteres supere los 600 bytes
         */
        while ((bytesLeidos = br.read(buffer)) != -1) {
            buffer = java.util.Arrays.copyOf(buffer, bytesLeidos);
            content.append(buffer);
            bytes = content.toString().getBytes(StandardCharsets.UTF_8);
            parts++;
            byte[] encodedBytes = Base64.getEncoder().encode(bytes);
            String result = new String(encodedBytes, StandardCharsets.UTF_8);
            data = new Data(Data.FILE_PART, parts, result);
            dataList.add(data);
            json = gson.toJson(data);
            partsList.add(json);
            content = new StringBuilder();
        }

        br.close();
        return partsList;
    }

    /**
     * Crea un objeto Data y su respectiva representacion en json con la
     * informacion del nombre archivo y la cantidad de fragmentos (numero de
     * paquetes o chunks) del archivo
     * 
     * @param fileName   el nombre del archivo a enviar
     * @param numPackets cantidad de fragmentos a enviar del archivo
     * @throws IOException en caso de que ocura un error al enviar el paquete
     */
    private void sendMetadata(String fileName, int numPackets) throws IOException {
        Data data = new Data(Data.FILE_NAME, 0, numPackets, fileName);
        String json = gson.toJson(data);
        byte[] buffer = json.getBytes();
        DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length, address, port);
        System.out.println("Sending metadata: " + data);
        socket.send(filePacket);
    }

    public void resendPackets(Data packet)throws IOException {
        System.out.println("looking for missing packets");
        String missingPackets = packet.getContent();
        String[] parts = missingPackets.split(",");
        List<String> missingParts = new ArrayList<>();
        for (String part : parts) {
            int partNum = Integer.parseInt(part);
            int index = dataList.indexOf(new Data(partNum));
            Data data = dataList.get(index);
            String json = gson.toJson(data);
            missingParts.add(json);
        }

        sendPackets(missingParts);
    }

    private String encode(byte[] bytes){
        String result = Base64.getEncoder().encodeToString(bytes);
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
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
        ClientHandler other = (ClientHandler) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    
}
