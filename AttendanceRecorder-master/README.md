Egyszerűsített rendszerleírás
Projekt neve
Egyetemi jelenlét-rögzítő rendszer (QR alapú, Java asztali alkalmazás)

1. Áttekintés
Egy könnyű, offline működő Java-alkalmazás, amely webkamera vagy laptop kamera segítségével beolvassa a hallgatók QR-kódját, és rögzíti a jelenlétet helyileg egy SQLite adatbázisban vagy CSV fájlban.

2. Célok
•	Az órai jelenlét automatizálása.
•	Papíralapú ívek kiváltása.
•	Minden hallgató naponta csak egyszer regisztrálhasson.
•	Azonnali visszajelzés a beolvasásról.

3. Funkcionális követelmények
Hallgatói oldal:
•	QR-kód beolvasása kamerán keresztül.
•	A QR tartalmazza: nevet, szakot, Neptun-kódot.
•	Visszajelzés:
o	„Sikeres rögzítés”
o	„Már regisztráltál ma!”
•	Megjeleníti, hogy hányszor vett részt az órákon.
Admin funkciók:
•	Összesített jelenlét megtekintése.
•	Exportálás CSV-be.

4. Nem funkcionális követelmények
•	Internetkapcsolat nélkül működik.
•	Egyszerű, átlátható felület (Swing vagy JavaFX).
•	OpenCV a kamera vezérléséhez.
•	ZXing a QR kód dekódolásához.
•	SQLite az adatok tartós tárolásához.

5. Adatbázis séma (SQLite)
attendance_records tábla:

Mező	        Típus	                                Leírás

id	          INTEGER PRIMARY KEY AUTOINCREMENT    	Egyedi azonosító
name	        TEXT	                                Hallgató neve
major	        TEXT	                                Szak
neptun	      TEXT	                                Neptun-kód
date	        TEXT	                                Dátum (YYYY-MM-DD)
scans	        INTEGER	                              Megjelenések száma
Megszorítás:
        • Egy hallgató naponta csak egyszer szerepelhet → UNIQUE(neptun, date)

6. Program működése:
-	Program elindul
-	Kamera inicializálás
-	QR-kód beolvasás és dekódolás (ZXing)
-	QR-adatok feldolgozása → név, szak, Neptun
-	Ellenőrzés az adatbázisban:
        o	Ha az adott napon nincs rekord → új bejegyzés.
        o	Ha már volt → hibaüzenet.
-	Visszajelzés a képernyőn (pl. „Pelda Anna – megjelent 3 alkalommal”)
-	Mentés SQLite adatbázisba

7. Technológiák
•	Java 17+
•	OpenCV – kamera elérés
•	ZXing – QR kód dekódolás
•	SQLite – adatok mentése
•	JavaFX – grafikus felület

8. Példa QR-adat
Name=Pelda Anna;Major=Mernokinformatikus;Neptun=ABC123

9. Jövőbeli fejlesztési lehetőségek
•	Admin felület belépéssel
•	Kurzusonkénti statisztikák
•	Export Excel/PDF formátumba
•	Email értesítések
