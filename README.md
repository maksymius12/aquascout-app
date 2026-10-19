# 🌊 AquaScout

**Плаваючий робот, який сам перевіряє воду, і Android-застосунок для керування ним.**
*A floating robot that checks water on its own, plus an Android app to control it.*

🇺🇦 [Українською](#-українською) · 🇬🇧 [English](#-english)

---

## 🇺🇦 Українською

### Проблема
Забруднення річок і озер помічають тоді, коли вже пізно: коли починається мор риби або вода «цвіте». Професійні прилади коштують тисячі доларів, а ручний відбір проб довгий і не дає даних у реальному часі.

### Рішення
AquaScout — катамаран завдовжки 40 см на гребних колесах. Він пропливає позначену зону змійкою, щосекунди міряє провідність води (скільки в ній розчинених солей і домішок) і передає дані на телефон по Bluetooth на відстань до 10 м.

### Можливості застосунку
| Функція | Статус |
|---|---|
| Вхід за e-mail і паролем (локально на телефоні) | ✅ |
| Пошук і підключення робота по Bluetooth (HC-05) | ✅ |
| Кілька роботів одночасно, зона ділиться між ними | ✅ |
| Ручне керування: тримаєш кнопку — їде, відпустиш — стоп | ✅ |
| Автостоп робота при втраті зв'язку | ✅ |
| Форма перевірки: тип водойми, місце, причина | ✅ |
| Точка на карті OpenStreetMap, пошук місця | ✅ |
| Розмітка зони, маршрут змійкою за часом | ✅ без GPS, приблизно |
| Графік провідності, оцінка води, поради «що робити» | ✅ |
| Історія перевірок | ✅ |
| Випробування на справжній водоймі | 🔶 у процесі |
| GPS, датчики каламутності, pH і температури | 🗺️ у планах |

### Залізо
| Компонент | Призначення |
|---|---|
| Arduino Uno R3 | мозок робота |
| HC-05 | Bluetooth, до ≈10 м, працює з Android |
| Keyestudio (щупи) | провідність води |
| L9110 + 2 × N20 6 В | рух на гребних колесах |
| 2 × 18650 (7,4 В) | живлення |
| Корпус | 3D-друк, PETG/ASA, модель в OpenSCAD |

Бюджет на електроніку — менше 1 500 грн.

### Як це працює
```
Датчик → Arduino Uno → HC-05 ─── Bluetooth ≤10 м ───→ Android-застосунок
                ↑                                              │
                └──────── команди руху F/B/L/R/S ←─────────────┘
```
- телефон → робот: `F` `B` `L` `R` `S`, швидкість `0`–`9`
- робот → телефон, раз на секунду: `W:<0..1023>;P:<0..100>`
- якщо 1,5 с немає команд, робот сам зупиняється

### Встановити
Відкрий **Releases → latest → AquaScout.apk** з телефона, завантаж і встанови. APK збирається автоматично (GitHub Actions) після кожного коміту.

Прошивка Arduino лежить у [`firmware/`](firmware/AquaScout_firmware/).

### Обмеження
- Робот міряє провідність, а не бактерії чи хімію. Для висновку про придатність води для пиття чи купання потрібен лабораторний аналіз.
- Маршрут рахується за часом, без GPS, тому вітер і течія збивають робота з курсу. Швидкість і час повороту калібруються в застосунку.
- HC-05 працює лише з Android.

---

## 🇬🇧 English

### Problem
Pollution in rivers and lakes is noticed too late — when fish start dying or the water blooms. Professional instruments cost thousands of dollars, and manual sampling is slow and gives no real-time data.

### Solution
AquaScout is a 40 cm paddle-wheel catamaran. It sweeps a marked area in a zigzag, measures water conductivity (dissolved salts and impurities) every second and sends the data to a phone over Bluetooth up to 10 m away.

### App features
| Feature | Status |
|---|---|
| E-mail + password sign-in (stored on the phone) | ✅ |
| Bluetooth scan and connect (HC-05) | ✅ |
| Several robots at once, area split between them | ✅ |
| Hold-to-drive manual control | ✅ |
| Robot auto-stop when the link is lost | ✅ |
| Check form: water type, location, reason | ✅ |
| OpenStreetMap location picker with search | ✅ |
| Area marking, time-based zigzag route | ✅ no GPS, approximate |
| Conductivity chart, verdict and advice | ✅ |
| Check history | ✅ |
| Field test on a real pond | 🔶 in progress |
| GPS, turbidity, pH and temperature sensors | 🗺️ planned |

### Hardware
Arduino Uno R3 · HC-05 Bluetooth · Keyestudio conductivity probes · L9110 driver + 2 × N20 6 V motors · 2 × 18650 (7.4 V) · 3D-printed PETG/ASA hull (OpenSCAD). Electronics budget: under UAH 1,500 (under $37).

### Protocol
- phone → robot: `F` `B` `L` `R` `S`, speed `0`–`9`
- robot → phone, once per second: `W:<0..1023>;P:<0..100>`
- the robot stops by itself after 1.5 s without commands

### Install
Open **Releases → latest → AquaScout.apk** on your phone and install it. The APK is built automatically by GitHub Actions on every commit.

### Limitations
Conductivity is not a lab test for bacteria or chemicals. The route is time-based without GPS, so wind and current cause drift. HC-05 works with Android only.

---

### Tech stack
Kotlin · Jetpack Compose · Material 3 · Android Bluetooth Classic (SPP) · MapLibre + OpenFreeMap · Nominatim · Arduino C++ · GitHub Actions

### Автор / Author
**Загоровський Максим / Maksym Zahorovskyi** — Архітектурно-інженерний колегіум А+, 2026
Керівник / Supervisor: **Альона Герасименко / Alona Herasymenko**

Карта © учасники [OpenStreetMap](https://www.openstreetmap.org/copyright).
