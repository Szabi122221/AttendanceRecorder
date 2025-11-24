package com.university.attendance;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Consumer;

/**
 * Vonalkód beolvasó funkcionalitás USB vonalkód olvasóhoz
 *
 * Ez az osztály kezeli a fizikai vonalkód olvasóból / billentyüzetből érkező adatokat,
 * feldolgozza a vonalkód tartalmát és rögzíti a jelenlétet.
 */
public class BarcodeScanner
{
    private DatabaseManager dbManager;
    private Consumer<String> statusUpdateCallback;

    /**
     * Konstruktor
     * @param dbManager Adatbázis kezelő kiegészítő
     * @param statusUpdateCallback Státusz frissítés (callback)
     */
    public BarcodeScanner(DatabaseManager dbManager,
                          Consumer<String> statusUpdateCallback)
    {
        this.dbManager = dbManager;
        this.statusUpdateCallback = statusUpdateCallback;
    }

    /**
     * Vonalkód input mező inicializálása
     * @param barcodeField A vonalkód beolvasó TextField, mivel ugy kezeli a pc mint egy sima periféria -> billentyűzet
     */
    public void setupBarcodeField(TextField barcodeField)
    {
        barcodeField.setOnAction(e -> handleBarcodeInput(barcodeField));
    }

    /**
     * Vonalkód input kezelése
     * @param barcodeField A vonalkód mező
     */
    private void handleBarcodeInput(TextField barcodeField)
    {
        String barcodeData = barcodeField.getText().trim();
        barcodeField.clear();

        if (barcodeData.isEmpty())
        {
            return;
        }

        System.out.println("Beolvasott vonalkód: " + barcodeData);

        // Vonalkód adat feldolgozása
        processCodeData(barcodeData, "Vonalkód");
    }

    /**
     * Vonalkód/QR-kód adat feldolgozása
     * @param codeData A beolvasott kód tartalma
     * @param codeType A kód típusa (Vonalkód vagy QR-kód)
     */
    public void processCodeData(String codeData, String codeType)
    {
        try
        {
            System.out.println("Feldolgozás: " + codeData);
            String name = "", major = "", neptun = "";

            // Ellenőrizzük, hogy strukturált adat-e (tartalmaz '=' karaktert) vagy csak Neptun kód
            if (codeData.contains("="))
            {
                // Strukturált formátum: Name=...;Major=...;Neptun=...
                System.out.println("Strukturált formátum feldolgozása");
                String[] parts = codeData.split(";");

                for (String part : parts)
                {
                    String[] keyValue = part.split("=");
                    if (keyValue.length == 2)
                    {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        switch (key)
                        {
                            case "Name":
                                name = value;
                                break;
                            case "Major":
                                major = value;
                                break;
                            case "Neptun":
                                neptun = value;
                                break;
                        }
                    }
                }
            }
            else
            {
                // Egyszerű formátum: csak Neptun kód (pl. ABC123)
                System.out.println("Egyszerű formátum (csak Neptun kód)");
                neptun = codeData.trim().toUpperCase();

                // Keressük meg a hallgatót közvetlenül az adatbázisban
                DatabaseManager.StudentInfo student = dbManager.getStudent(neptun);
                if (student != null)
                {
                    name = student.getName();
                    major = student.getMajor();
                    neptun = student.getNeptun();
                    System.out.println("Hallgató megtalálva: " + name + " - " + neptun + " (" + major + ")");
                }
                else
                {
                    System.out.println("Hallgató nem található a rendszerben: " + neptun);
                    updateStatus("Ismeretlen Neptun kód: " + neptun);
                    return;
                }
            }

            System.out.println("Eredmény - Név: '" + name + "', Szak: '" + major + "', Neptun: '" + neptun + "'");

            if (name.isEmpty() || neptun.isEmpty())
            {
                updateStatus("Helytelen " + codeType + " Formátum!");
                return;
            }

            // Ellenőrizzük, hogy ma már beolvasták-e
            LocalDate today = LocalDate.now();
            LocalTime scanTime = LocalTime.now();

            if (dbManager.hasScannedToday(neptun, today.toString()))
            {
                updateStatus(name + " ma már be lett olvasva!");
            }
            else
            {
                // Jelenlét rögzítése időbélyeggel
                dbManager.recordAttendance(name, major, neptun, today.toString(), scanTime);

                updateStatus(name + " sikeresen beolvasva! (" + codeType + ")");
            }

            // Státusz visszaállítása 3 másodperc után
            resetStatusAfterDelay();

        }
        catch (Exception e)
        {
            updateStatus("Hiba történt a " + codeType + " feldolgozása során!");
            e.printStackTrace();
        }
    }

    /**
     * Státusz frissítése
     */
    private void updateStatus(String message)
    {
        if (statusUpdateCallback != null)
        {
            statusUpdateCallback.accept(message);
        }
    }

    /**
     * Státusz visszaállítása 3 másodperc késleltetéssel
     */
    private void resetStatusAfterDelay()
    {
        new Thread(() ->
        {
            try
            {
                Thread.sleep(3000);
                Platform.runLater(() ->
                {
                    updateStatus("Készen Áll");
                });
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }).start();
    }
}
