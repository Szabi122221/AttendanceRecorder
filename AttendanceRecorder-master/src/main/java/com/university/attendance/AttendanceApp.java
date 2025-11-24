package com.university.attendance;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class AttendanceApp extends Application 
{
    // Kamera objektum a videó stream kezeléséhez
    private VideoCapture camera;
    
    // UI komponensek a kamera kép és állapot megjelenítéséhez
    private ImageView imageView;
    private Label statusLabel;
    private Label attendanceCountLabel;
    private TextField barcodeInputField;

    // Háttérszál a folyamatos képfeldolgozáshoz
    private ScheduledExecutorService executor;

    // Adatbázis kezelő a jelenléti adatok tárolásához
    private DatabaseManager dbManager;

    // Vonalkód scanner és hallgató kezelő
    private BarcodeScanner barcodeScanner;
    private StudentManager studentManager;
    
    // Flag a többszörös QR feldolgozás elkerülésére
    private volatile boolean isProcessing = false;
    
    // Utolsó sikeresen beolvasott Neptun kód és időbélyeg
    private volatile String lastScannedNeptun = "";
    private volatile long lastScanTime = 0;
    
    // OpenCV natív könyvtár betöltése az alkalmazás indításakor
    static 
    {
        nu.pattern.OpenCV.loadLocally();
    }
    
    @Override
    public void start(Stage primaryStage) 
    {
        // Adatbázis inicializálás és táblák létrehozása
        dbManager = new DatabaseManager();
        dbManager.initDatabase();

        // BarcodeScanner inicializálása
        barcodeScanner = new BarcodeScanner(
            dbManager,
            message -> statusLabel.setText(message)
        );

        // StudentManager inicializálása
        studentManager = new StudentManager(dbManager);

        // Fő Konténer létrehozása
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        // Cím
        Label titleLabel = new Label("Egyetemi Jelenlét Rögzítő Rendszer");

        // === VONALKÓD BEVITELI MEZŐ ===
        VBox barcodeSection = new VBox(10);
        barcodeSection.setAlignment(Pos.CENTER);
        barcodeSection.setPadding(new Insets(10));

        Label barcodeLabel = new Label("Vonalkód / Neptun kód beolvasás:");

        barcodeInputField = new TextField();
        barcodeInputField.setPromptText("Szkennelj be egy vonalkódot vagy írd be a Neptun kódot...");
        barcodeInputField.setPrefWidth(500);

        // BarcodeScanner beállítása a TextField-re
        barcodeScanner.setupBarcodeField(barcodeInputField);

        barcodeSection.getChildren().addAll(barcodeLabel, barcodeInputField);

        // Kamera képet megjelenítő terület
        imageView = new ImageView();
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setPreserveRatio(true);

        // Állapot címke (sikeres/sikertelen beolvasás)
        statusLabel = new Label("Készen Áll");

        // Jelenlétet számláló címke
        attendanceCountLabel = new Label("");
        
        // Gombok elrendezése
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button startButton = new Button("Kamera Indítása");
        Button stopButton = new Button("Kamera Leállítása");
        Button addStudentButton = new Button("Új Hallgató Felvitele");
        Button viewRecordsButton = new Button("Összes Hallgató Listázása");

        stopButton.setDisable(true);

        startButton.setOnAction(e ->
        {
            startCamera();
            startButton.setDisable(true);
            stopButton.setDisable(false);
        });

        stopButton.setOnAction(e ->
        {
            stopCamera();
            startButton.setDisable(false);
            stopButton.setDisable(true);
        });

        addStudentButton.setOnAction(e -> studentManager.showAddStudentForm());

        viewRecordsButton.setOnAction(e -> showRecordsWindow());

        buttonBox.getChildren().addAll(startButton, stopButton, addStudentButton, viewRecordsButton);

        // Összes UI elem hozzáadása a fő konténerhez
        root.getChildren().addAll(titleLabel, barcodeSection, imageView, statusLabel,
                                   attendanceCountLabel, buttonBox);

        // Jelenet és ablak beállítása
        Scene scene = new Scene(root, 700, 850);
        primaryStage.setTitle("QR Jelenlét Rögzítő");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> cleanup());
        primaryStage.show();
    }
    
    
    // Kamera inicializálás és folyamatos képfeldolgozás indítása
    
    private void startCamera() 
    {
        // Alapértelmezett kamera megnyitása 
        camera = new VideoCapture(0);
        
        if (!camera.isOpened()) 
        {
            showAlert("Kamera Hiba", "A kamerát nem sikerült elérni!");
            return;
        }
        
        // Háttérszál indítása 30 FPS-sel (33ms-onként olvas be képet)
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::processFrame, 0, 33, TimeUnit.MILLISECONDS);
    }
    
    
    // Egyetlen kamera frame feldolgozása: kép megjelenítés és QR dekódolás
    
    private void processFrame() 
    {
        if (camera == null || !camera.isOpened()) 
        {
            return;
        }
        
        Mat frame = new Mat();
        camera.read(frame);
        
        if (!frame.empty()) 
        {
            // OpenCV Mat konvertálása JavaFX Image-be és megjelenítés
            Image img = matToImage(frame);
            Platform.runLater(() -> imageView.setImage(img));
            
            // QR kód dekódolás megkísérlése (ha nincs épp feldolgozás alatt)
            if (!isProcessing) 
            {
                isProcessing = true;
                String qrData = decodeQRCode(frame);
                
                if (qrData != null) 
                {
                    // UI frissítés a JavaFX Application szálon
                    Platform.runLater(() -> handleQRData(qrData));
                }
                isProcessing = false;
            }
            
            // Mat felszabadítása
            frame.release();
        }
    }
    
    
    // QR kód dekódolás a képből a ZXing könyvtár segítségével
    // return: QR kód szöveg, vagy null ha nincs érvényes QR kód
    
    private String decodeQRCode(Mat frame) 
    {
        try 
        {
            // OpenCV Mat -> BufferedImage konverzió
            BufferedImage bufferedImage = matToBufferedImage(frame);
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            // QR kód dekódolás
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } 
        catch (NotFoundException e) 
        {
            // Nincs QR kód
            return null;
        }
    }
    

    // Beolvasott QR kód adat feldolgozása és adatbázisba mentés
    // Elfogadott formátum: Name=Pelda János;Major=PeldaMernok;Neptun=ABC123
    
    private void handleQRData(String qrData) 
    {
        try 
        {
            // Duplikált beolvasás elkerülése
            long currentTime = System.currentTimeMillis();
            if (qrData.contains(lastScannedNeptun) && 
                (currentTime - lastScanTime) < 3000) 
            {
                return;
            }
            
            // QR adat szétbontása kulcs-érték párokra
            String[] parts = qrData.split(";");
            String name = "", major = "", neptun = "";
            
            for (String part : parts) 
            {
                String[] keyValue = part.split("=");
                if (keyValue.length == 2) 
                {
                    switch (keyValue[0].trim()) 
                    {
                        case "Name":
                            name = keyValue[1].trim();
                            break;
                        case "Major":
                            major = keyValue[1].trim();
                            break;
                        case "Neptun":
                            neptun = keyValue[1].trim();
                            break;
                    }
                }
            }
            
            // Kötelező mezők ellenőrzése
            if (name.isEmpty() || neptun.isEmpty()) 
            {
                statusLabel.setText("Helytelen QR-kód Formátum!");
                statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                return;
            }
            
            // Mai dátum ellenőrzése mert egy hallgató naponta csak egyszer jelentkezhet be
            LocalDate today = LocalDate.now();
            if (dbManager.hasScannedToday(neptun, today.toString())) 
            {
                statusLabel.setText("Ma Már Beszkennelted a Kódot!");
                statusLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: orange; -fx-font-weight: bold;");
                attendanceCountLabel.setText("");
            } 
            else 
            {
                // Jelenlét rögzítése az adatbázisban
                dbManager.recordAttendance(name, major, neptun, today.toString());
                int totalScans = dbManager.getTotalScans(neptun);

                // Sikeres beolvasás jelzése
                statusLabel.setText("Sikeres Adatrögzítés!");
                statusLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: green; -fx-font-weight: bold;");
                attendanceCountLabel.setText(String.format("%s - Megjelent ennyi alkalommal: %d", name, totalScans));
                
                // Utolsó sikeres beolvasás mentése
                lastScannedNeptun = neptun;
                lastScanTime = currentTime;
            }
            
            // Állapot visszaállítása 3 másodperc után
            new Thread(() -> 
            {
                try 
                {
                    Thread.sleep(3000);
                    Platform.runLater(() -> 
                    {
                        statusLabel.setText("Készen Áll a QR-kód Szkennelésre");
                        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #555;");
                        attendanceCountLabel.setText("");
                    });
                } 
                catch (InterruptedException e) 
                {
                    e.printStackTrace();
                }
            }).start();
            
        } 
        catch (Exception e) 
        {
            statusLabel.setText("Hiba történt a QR feldolgozása során!");
            statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
            e.printStackTrace();
        }
    }
    
    
    // Új ablak megnyitása az összes jelenléti rekord megjelenítésére
    
    private void showRecordsWindow() 
    {
        Stage recordsStage = new Stage();
        recordsStage.setTitle("Minden Jelenléti Adat");
        
        // Szöveges terület az adatok megjelenítésére
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        
        // Összes rekord lekérése és megjelenítése
        String records = dbManager.getAllRecords();
        textArea.setText(records);
        
        // CSV export gomb
        Button exportButton = new Button("Export CSV-be");
        exportButton.setOnAction(e -> 
        {
            dbManager.exportToCSV("attendance_export.csv");
            showAlert("Export Kész", "Az Adatok Exportálva Ide: attendance_export.csv");
        });
        
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        vbox.getChildren().addAll(textArea, exportButton);
        
        Scene scene = new Scene(vbox, 800, 600);
        recordsStage.setScene(scene);
        recordsStage.show();
    }
    
    
    // Kamera leállítása és erőforrások felszabadítása
    
    private void stopCamera() 
    {
        if (executor != null) 
        {
            executor.shutdown();
        }
        if (camera != null && camera.isOpened()) 
        {
            camera.release();
        }
    }
    
    
    // Teljes felszabadítás az alkalmazás bezárásakor
    
    private void cleanup() 
    {
        stopCamera();
        if (dbManager != null) 
        {
            dbManager.close();
        }
    }
    
    
    // OpenCV Mat konvertálása JavaFX Image objektummá
    
    private Image matToImage(Mat frame) 
    {
        MatOfByte buffer = new MatOfByte();
        org.opencv.imgcodecs.Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
    
    
    // OpenCV Mat konvertálása Java BufferedImage objektummá ZXing-nek
    
    private BufferedImage matToBufferedImage(Mat mat) 
    {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) 
        {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        mat.get(0, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData());
        return image;
    }
    
    
    // Egyszerű információs ablak megjelenítése
    
    private void showAlert(String title, String message) 
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void main(String[] args) 
    {
        launch(args);
    }
}