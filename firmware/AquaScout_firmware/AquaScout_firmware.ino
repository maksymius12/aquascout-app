/*
  AquaScout — прошивка для Arduino Uno R3
  ---------------------------------------
  Підключення (підписи як на платі):
    L9110:  A-IA -> ~5   A-IB -> ~6   (мотор M1, лівий)
            B-IA -> ~9   B-IB -> ~10  (мотор M2, правий)
            VCC  -> "+" батарейок (7,4 В)   GND -> GND
    HC-05:  TXD -> 2
            RXD <- ~3 через дільник (1 кОм від піна 3, 2 кОм від RXD на GND)
            VCC -> 5V   GND -> GND
    Датчик Keyestudio (S V G):
            S -> A0   V -> 7   G -> GND
            (живимо з піна 7 лише під час заміру — щупи менше іржавіють у воді)
    Батарейки: "+" -> VIN, "-" -> GND

  Команди з телефона (один символ, Bluetooth 9600):
    F — вперед   B — назад   L — ліворуч   R — праворуч   S — стоп
    0..9 — швидкість (0 = 0%, 9 = максимум, обмежений MAX_PWM)
    ? — надіслати замір зараз
  Безпека: якщо 1,5 с не прийшло жодної команди, робот зупиняється.
  Тому застосунок повторює поточну команду (наприклад F) кожні ~0,5 с.

  Що робот надсилає (раз на секунду, рядок з \n):
    W:<0..1023>;P:<0..100>
      W — сирий сигнал датчика (більше = вода краще проводить струм,
          тобто більше розчинених солей/домішок)
      P — те саме у відсотках між калібруваннями DRY_RAW і SALT_RAW
*/

#include <SoftwareSerial.h>

// ---------- піни ----------
const uint8_t PIN_BT_RX   = 2;   // сюди приходить TXD з HC-05
const uint8_t PIN_BT_TX   = 3;   // звідси йде на RXD HC-05 (через дільник)
const uint8_t PIN_A_IA    = 5;
const uint8_t PIN_A_IB    = 6;
const uint8_t PIN_B_IA    = 9;
const uint8_t PIN_B_IB    = 10;
const uint8_t PIN_SENS_V  = 7;   // живлення датчика
const uint8_t PIN_SENS_S  = A0;  // сигнал датчика

// ---------- налаштування ----------
const uint8_t  MAX_PWM        = 180;   // ~70%: мотори N20 на 6 В, а батарейки дають 7,4 В
const uint16_t CMD_TIMEOUT_MS = 1500;  // стоп, якщо команди не приходять
const uint16_t SEND_EVERY_MS  = 1000;  // як часто надсилати заміри
const bool     INVERT_LEFT    = false; // якщо лівий мотор крутиться не туди — постав true
const bool     INVERT_RIGHT   = false; // те саме для правого

// Калібрування датчика (заміряй і впиши свої значення, див. інструкцію внизу)
const int DRY_RAW  = 0;    // щупи в повітрі
const int SALT_RAW = 700;  // щупи в солоній воді (1 ч. л. солі на склянку)

SoftwareSerial bt(PIN_BT_RX, PIN_BT_TX);

uint8_t speedPwm = MAX_PWM;
char    lastCmd  = 'S';
unsigned long lastCmdAt  = 0;
unsigned long lastSendAt = 0;

// ---------- мотори ----------
// v: від -255 до 255 (мінус = назад)
void setMotor(uint8_t pinIA, uint8_t pinIB, int v, bool invert) {
  if (invert) v = -v;
  v = constrain(v, -255, 255);
  if (v > 0)      { analogWrite(pinIA, v);  digitalWrite(pinIB, LOW); }
  else if (v < 0) { digitalWrite(pinIA, LOW); analogWrite(pinIB, -v); }
  else            { digitalWrite(pinIA, LOW); digitalWrite(pinIB, LOW); }
}

void drive(int left, int right) {
  setMotor(PIN_A_IA, PIN_A_IB, left,  INVERT_LEFT);
  setMotor(PIN_B_IA, PIN_B_IB, right, INVERT_RIGHT);
}

void applyCommand(char c) {
  int s = speedPwm;
  switch (c) {
    case 'F': drive( s,  s); break;
    case 'B': drive(-s, -s); break;
    case 'L': drive(-s,  s); break;   // розворот на місці
    case 'R': drive( s, -s); break;
    default:  drive( 0,  0); c = 'S'; break;
  }
  lastCmd = c;
}

// ---------- датчик ----------
int readSensorRaw() {
  digitalWrite(PIN_SENS_V, HIGH);
  delay(20);                         // даємо датчику стабілізуватися
  long sum = 0;
  for (uint8_t i = 0; i < 10; i++) { sum += analogRead(PIN_SENS_S); delay(2); }
  digitalWrite(PIN_SENS_V, LOW);     // вимикаємо, щоб щупи не іржавіли
  return sum / 10;
}

void sendReading() {
  int raw = readSensorRaw();
  int pct = map(constrain(raw, DRY_RAW, SALT_RAW), DRY_RAW, SALT_RAW, 0, 100);
  String line = "W:" + String(raw) + ";P:" + String(pct);
  bt.println(line);
  Serial.println(line);              // для перевірки в Serial Monitor
}

// ---------- старт ----------
void setup() {
  pinMode(PIN_A_IA, OUTPUT); pinMode(PIN_A_IB, OUTPUT);
  pinMode(PIN_B_IA, OUTPUT); pinMode(PIN_B_IB, OUTPUT);
  pinMode(PIN_SENS_V, OUTPUT); digitalWrite(PIN_SENS_V, LOW);
  drive(0, 0);

  Serial.begin(9600);
  bt.begin(9600);                    // стандартна швидкість HC-05
  Serial.println(F("AquaScout ready"));
  bt.println(F("AquaScout ready"));
}

// ---------- головний цикл ----------
void loop() {
  // 1) команди з телефона або з Serial Monitor (зручно для тесту без телефона)
  while (bt.available() || Serial.available()) {
    char c = bt.available() ? bt.read() : Serial.read();
    if (c >= 'a' && c <= 'z') c -= 32;             // f -> F
    if (c >= '0' && c <= '9') {
      speedPwm = map(c - '0', 0, 9, 0, MAX_PWM);
      applyCommand(lastCmd);                       // нова швидкість одразу
      lastCmdAt = millis();
    } else if (c == '?') {
      sendReading();
    } else if (c == 'F' || c == 'B' || c == 'L' || c == 'R' || c == 'S') {
      applyCommand(c);
      lastCmdAt = millis();
    }
    // інші символи (\r, \n, пробіли) ігноруємо
  }

  // 2) безпека: немає команд — стоп
  if (lastCmd != 'S' && millis() - lastCmdAt > CMD_TIMEOUT_MS) {
    applyCommand('S');
    bt.println(F("STOP:timeout"));
  }

  // 3) заміри раз на секунду
  if (millis() - lastSendAt >= SEND_EVERY_MS) {
    lastSendAt = millis();
    sendReading();
  }
}

/*
  ЯК ПЕРЕВІРИТИ І ВІДКАЛІБРУВАТИ
  1. Завантаж скетч БЕЗ підключеного HC-05 до пінів 0/1 (він на 2/3 — усе ок).
  2. Відкрий Serial Monitor, 9600. Кожну секунду побачиш W:...;P:...
  3. Щупи в повітрі -> запиши W, це DRY_RAW.
     Щупи в склянці води з 1 ч. л. солі -> запиши W, це SALT_RAW.
     Впиши обидва числа вгорі скетчу і завантаж ще раз.
  4. У Serial Monitor надішли F, L, R, B, S — мотори мають крутитися.
     Якщо котрийсь крутиться не туди — INVERT_LEFT / INVERT_RIGHT = true.
  5. Телефон: спарюй HC-05 (PIN 1234 або 0000), відкрий будь-який
     "Serial Bluetooth Terminal" і надсилай ті самі літери.
*/
