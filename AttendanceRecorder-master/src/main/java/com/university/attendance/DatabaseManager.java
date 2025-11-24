package com.university.attendance;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;



 
public class DatabaseManager 
{
    // SQLite lokális útvonala 
    private static final String DB_URL = "jdbc:sqlite:attendance.db";
    
    // Adatbázis kapcsolat objektum
    private Connection connection;
    
    
    // Adatbázis inicializálás: kapcsolat létrehozása és táblák előkészítése
    
    public void initDatabase()
    {
        try
        {
            connection = DriverManager.getConnection(DB_URL);
            createTable();
            createStudentsTable();
        }
        catch (SQLException e)
        {
            System.err.println("Adatbázis inicializálási hiba: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    // Jelenléti rekordok táblájának létrehozása ha még nem létezik
    // UNIQUE constraint --> egy hallgató naponta csak egyszer kerülhet be
    
    private void createTable() 
    {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS attendance_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                major TEXT NOT NULL,
                neptun TEXT NOT NULL,
                date TEXT NOT NULL,
                scans INTEGER DEFAULT 1,
                UNIQUE(neptun, date)
            )
        """;
        
        try (Statement stmt = connection.createStatement()) 
        {
            stmt.execute(createTableSQL);
        } 
        catch (SQLException e) 
        {
            System.err.println("Tábla létrehozási hiba: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
     // Ellenőrzi, hogy egy hallgatót ma már beszkennelt-e
     // a return érték true ha ma már volt beolvasás, false ha nem
    
    public boolean hasScannedToday(String neptun, String date) 
    {
        String query = "SELECT COUNT(*) FROM attendance_records WHERE neptun = ? AND date = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(query)) 
        {
            pstmt.setString(1, neptun);
            pstmt.setString(2, date);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) 
            {
                return rs.getInt(1) > 0;
            }
        } 
        catch (SQLException e) 
        {
            System.err.println("Lekérdezési hiba (hasScannedToday): " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    
    // Jelenlét rögzítése az adatbázisban
    
    public void recordAttendance(String name, String major, String neptun, String date) 
    {
        String insertSQL = "INSERT INTO attendance_records (name, major, neptun, date, scans) VALUES (?, ?, ?, ?, 1)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) 
        {
            pstmt.setString(1, name);
            pstmt.setString(2, major);
            pstmt.setString(3, neptun);
            pstmt.setString(4, date);
            pstmt.executeUpdate();
        } 
        catch (SQLException e) 
        {
            // Hibaüzenet, ha UNIQUE constraint sérül
            System.err.println("Rögzítési hiba: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
     // Hallgató összes jelenléti napjának száma
     // return: Hány különböző napon volt beolvasva az adott kód
    
    public int getTotalScans(String neptun) 
    {
        String query = "SELECT COUNT(*) FROM attendance_records WHERE neptun = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(query)) 
        {
            pstmt.setString(1, neptun);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) 
            {
                return rs.getInt(1);
            }
        } 
        catch (SQLException e) 
        {
            System.err.println("Lekérdezési hiba (getTotalScans): " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }
    
    
     // Összes jelenléti rekord lekérése formázott szövegként
     // return: Táblázatos formátumú string az összes rekorddal
     
    public String getAllRecords() 
    {
        StringBuilder sb = new StringBuilder();
        
        // Táblázat fejléc
        sb.append(String.format("%-5s %-25s %-30s %-10s %-12s %-6s%n", 
                  "ID", "Name", "Major", "Neptun", "Date", "Scans"));
        sb.append("=".repeat(100)).append("\n");
        
        // Összes rekord lekérése dátum szerint csökkenő sorrendben
        String query = "SELECT * FROM attendance_records ORDER BY date DESC, id DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) 
        {
            // Minden sor hozzáadása a formázott szöveghez
            while (rs.next()) 
            {
                sb.append(String.format("%-5d %-25s %-30s %-10s %-12s %-6d%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("major"),
                        rs.getString("neptun"),
                        rs.getString("date"),
                        rs.getInt("scans")));
            }
        } 
        catch (SQLException e) 
        {
            System.err.println("Lekérdezési hiba (getAllRecords): " + e.getMessage());
            e.printStackTrace();
            return "Hiba a rekordok lekérésekor: " + e.getMessage();
        }
        
        return sb.toString();
    }
    
    //  Adatok exportálása CSV fájlba
     
    public void exportToCSV(String filename) 
    {
        String query = "SELECT * FROM attendance_records ORDER BY date DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query);
             FileWriter writer = new FileWriter(filename)) 
        {
            // CSV fejléc
            writer.append("ID,Name,Major,Neptun,Date,Scans\n");
            
            // Minden rekord írása CSV formátumban
            while (rs.next()) 
            {
                writer.append(String.format("%d,%s,%s,%s,%s,%d%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("major"),
                        rs.getString("neptun"),
                        rs.getString("date"),
                        rs.getInt("scans")));
            }
            
            System.out.println("CSV export sikeres: " + filename);
            
        } 
        catch (SQLException | IOException e) 
        {
            System.err.println("Export hiba: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

     // Adatbázis kapcsolat megszüntetése

    public void close()
    {
        try
        {
            if (connection != null && !connection.isClosed())
            {
                connection.close();
                System.out.println("Adatbázis kapcsolat lezárva.");
            }
        }
        catch (SQLException e)
        {
            System.err.println("Kapcsolat lezárási hiba: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // ================================= KIEGÉSZÍTŐk A VONALKÓDOLVASÓHOZ =====

    /**
     * Students tábla létrehozása
     * Mert ha még nem volt beolvasva aegy hallgató akkor nem lehet leolvasni
     * ha csak a attendace_recordot használom.
     */
    private void createStudentsTable()
    {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS students (
                        neptun TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        major TEXT NOT NULL
            )
        """;

        try (Statement stmt = connection.createStatement())
        {
            stmt.execute(createTableSQL);
            System.out.println("Students tábla létrehozva (üres - még nincs hallgató)");
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Új hallgató hozzáadása az adatbázishoz
     */
    public void insertStudent(String name, String major, String neptun)
    {
        String insertSQL = "INSERT OR REPLACE INTO students (name, major, neptun) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL))
        {
            pstmt.setString(1, name);
            pstmt.setString(2, major);
            pstmt.setString(3, neptun.toUpperCase());
            pstmt.executeUpdate();
            System.out.println("Hallgató mentve adatbázisba: " + name + " (" + neptun + ")");
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Hallgató keresése Neptun kód alapján
     */
    public StudentInfo getStudent(String neptun)
    {
        String query = "SELECT * FROM students WHERE neptun = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query))
        {
            String searchNeptun = neptun.toUpperCase();
            pstmt.setString(1, searchNeptun);

            System.out.println("DEBUG: Keresés - Neptun: '" + searchNeptun + "'");

            ResultSet rs = pstmt.executeQuery();

            if (rs.next())
            {
                System.out.println("DEBUG: Találat - Név: " + rs.getString("name"));
                return new StudentInfo(
                    rs.getString("name"),
                    rs.getString("major"),
                    rs.getString("neptun")
                );
            }
            else
            {
                System.out.println("DEBUG: Nincs találat a students táblában!");

                // Összes hallgató kilistázása debug céljából
                Statement stmt = connection.createStatement();
                ResultSet allStudents = stmt.executeQuery("SELECT neptun, name FROM students");
                System.out.println("DEBUG: Students táblában lévő Neptun kódok:");
                while (allStudents.next())
                {
                    System.out.println("  - " + allStudents.getString("neptun") + " (" + allStudents.getString("name") + ")");
                }
            }
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Ellenőrzi, hogy létezik-e a hallgató
     */
    public boolean hasStudent(String neptun)
    {
        String query = "SELECT COUNT(*) FROM students WHERE neptun = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query))
        {
            pstmt.setString(1, neptun.toUpperCase());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next())
            {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
        return false;
    }



    /**
     * Jelenlét rögzítése időbélyeggel
     */
    public void recordAttendance(String name, String major, String neptun, String date, LocalTime scanTime)
    {
        recordAttendance(name, major, neptun, date);
    }

    /**
     * Hallgató információk tárolására szolgáló belső osztály
     */
    public static class StudentInfo
    {
        private String name;
        private String major;
        private String neptun;

        public StudentInfo(String name, String major, String neptun)
        {
            this.name = name;
            this.major = major;
            this.neptun = neptun;
        }

        public String getName()
        {
            return name;
        }

        public String getMajor()
        {
            return major;
        }

        public String getNeptun()
        {
            return neptun;
        }
    }
}