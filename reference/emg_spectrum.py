"""Desktop EMG serial acquisition and real-time spectrum analysis (PyQt5).

This is the original desktop tool kept for protocol / DSP comparison with the
Android port. It is an engineering acquisition and visualization program, not a
medical device.
"""

import datetime  # noqa: F401  (kept from original desktop imports)
import sys
import time  # noqa: F401
import serial
import serial.tools.list_ports
import numpy as np
from PyQt5 import QtWidgets
from PyQt5.QtCore import QTimer
from PyQt5.QtGui import QFont
from matplotlib.backends.backend_qt5agg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
from scipy.signal import butter, filtfilt, iirnotch, sosfiltfilt  # noqa: F401
from scipy.signal.windows import hann  # noqa: F401


FS = 3910
SAMPLES = FS
TIME_VIEW_S = 0.1


def bandpass_filter(data, low_cut=15, high_cut=570, fs=FS, order=5):
    sos = butter(order, [low_cut, high_cut], btype="bandpass", fs=fs, output="sos")
    return sosfiltfilt(sos, data)


def notch_filter_sos(data):
    sos = butter(2, [48, 52], btype="bandstop", fs=FS, output="sos")
    return sosfiltfilt(sos, data)


def compute_fft_clean(data):
    N = len(data)
    data = data - np.mean(data)
    window = np.hanning(N)
    data = data * window
    X = np.fft.fft(data)
    freqs = np.fft.fftfreq(N, d=1 / FS)
    mask = freqs >= 0
    scale = 2 / np.sum(window)
    return freqs[mask], np.abs(X[mask]) * scale


def compute_rms(data):
    return np.sqrt(np.mean(data ** 2))


def parse_emg_packet(packet: bytes):
    if len(packet) < 4:
        return None
    if packet[0] != 0x24:
        return None
    checksum = sum(packet[:-1]) & 0xFF
    if checksum != packet[-1]:
        print(f"校验失败！{checksum},{packet[-1]}")
        # desktop continues despite checksum fail
    data_bytes = packet[1:-1]
    try:
        adc_val = int(data_bytes.decode("ascii"))
    except Exception as e:
        print("解析错误:", e)
        return None
    voltage_mv = (adc_val - (1 << 23)) * (1000 * 0.023) / (1 << 23)
    return voltage_mv


class MainWindow(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("肌电信号采集与频谱分析")
        self.resize(1100, 720)

        self.ser = None
        self.buffer = []
        self.timer = QTimer(self)
        self.timer.setInterval(200)
        self.timer.timeout.connect(self.on_timer)

        self.port_combo = QtWidgets.QComboBox()
        self.btn_refresh = QtWidgets.QPushButton("刷新串口")
        self.btn_start = QtWidgets.QPushButton("开始")
        self.btn_stop = QtWidgets.QPushButton("停止")
        self.btn_stop.setEnabled(False)
        self.metrics = QtWidgets.QLabel("主频: --    峰值: --    峰峰值: --    RMS值: --")
        self.metrics.setFont(QFont("Sans Serif", 12))

        self.btn_refresh.clicked.connect(self.refresh_ports)
        self.btn_start.clicked.connect(self.start_acq)
        self.btn_stop.clicked.connect(self.stop_acq)

        top = QtWidgets.QHBoxLayout()
        top.addWidget(QtWidgets.QLabel("串口"))
        top.addWidget(self.port_combo, 1)
        top.addWidget(self.btn_refresh)
        top.addWidget(self.btn_start)
        top.addWidget(self.btn_stop)

        self.figure = Figure(figsize=(10, 6), tight_layout=True)
        self.ax_time = self.figure.add_subplot(211)
        self.ax_freq = self.figure.add_subplot(212)
        self.canvas = FigureCanvas(self.figure)
        self._init_axes()

        layout = QtWidgets.QVBoxLayout(self)
        layout.addLayout(top)
        layout.addWidget(self.metrics)
        layout.addWidget(self.canvas, 1)

        self.refresh_ports()

    def _init_axes(self):
        self.ax_time.set_title("时域信号")
        self.ax_time.set_xlabel("时间 (s)")
        self.ax_time.set_ylabel("µV")
        self.ax_time.set_xlim(0, TIME_VIEW_S)
        self.ax_freq.set_title("频谱")
        self.ax_freq.set_xlabel("频率 (Hz)")
        self.ax_freq.set_ylabel("µV")
        self.ax_freq.set_xlim(0, 600)
        self.line_time, = self.ax_time.plot([], [], color="#1976D2", linewidth=1.0)
        self.line_freq, = self.ax_freq.plot([], [], color="#E65100", linewidth=1.0)

    def refresh_ports(self):
        current = self.port_combo.currentText()
        self.port_combo.clear()
        ports = serial.tools.list_ports.comports()
        for p in ports:
            label = p.device if not p.description else f"{p.device} ({p.description})"
            self.port_combo.addItem(label, p.device)
        idx = self.port_combo.findData(current)
        if idx >= 0:
            self.port_combo.setCurrentIndex(idx)

    def start_acq(self):
        if self.ser is not None:
            return
        port = self.port_combo.currentData() or self.port_combo.currentText()
        if not port:
            QtWidgets.QMessageBox.warning(self, "提示", "未选择串口")
            return
        try:
            self.ser = serial.Serial(
                port=port,
                baudrate=115200,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE,
                timeout=0.05,
                xonxoff=False,
                rtscts=False,
                dsrdtr=False,
            )
            self.ser.reset_input_buffer()
        except Exception as e:
            self.ser = None
            QtWidgets.QMessageBox.critical(self, "串口错误", str(e))
            return
        self.buffer = []
        self.btn_start.setEnabled(False)
        self.btn_stop.setEnabled(True)
        self.btn_refresh.setEnabled(False)
        self.timer.start()

    def stop_acq(self):
        self.timer.stop()
        if self.ser is not None:
            try:
                self.ser.close()
            except Exception:
                pass
            self.ser = None
        self.btn_start.setEnabled(True)
        self.btn_stop.setEnabled(False)
        self.btn_refresh.setEnabled(True)

    def closeEvent(self, event):
        self.stop_acq()
        event.accept()

    def on_timer(self):
        if self.ser is None:
            return
        try:
            while self.ser.in_waiting:
                packet = self.ser.readline()
                packet = packet.replace(b"\r", b"").replace(b"\n", b"")
                val = parse_emg_packet(packet)
                if val is not None:
                    self.buffer.append(val)
        except Exception as e:
            print("读取错误:", e)
            self.stop_acq()
            return

        if len(self.buffer) < SAMPLES:
            return

        data = np.asarray(self.buffer[:SAMPLES], dtype=float)
        self.buffer = self.buffer[SAMPLES:]
        self._update_plots(data)

    def _update_plots(self, data):
        filtered = bandpass_filter(data)
        filtered = notch_filter_sos(filtered)
        freqs, spec = compute_fft_clean(filtered)

        centered_uv = (filtered - np.mean(filtered)) * 1000.0
        n_view = int(TIME_VIEW_S * FS)
        t = np.arange(n_view) / FS
        y = centered_uv[:n_view]
        self.line_time.set_data(t, y)
        self.ax_time.set_xlim(0, TIME_VIEW_S)
        y_min = float(np.min(y))
        y_max = float(np.max(y))
        pad = (y_max - y_min) * 0.15
        if pad <= 1e-9:
            pad = 1.0
        self.ax_time.set_ylim(y_min - pad, y_max + pad)

        mask = freqs <= 600
        fx = freqs[mask]
        fy = spec[mask] * 1000.0
        self.line_freq.set_data(fx, fy)
        self.ax_freq.set_xlim(0, 600)
        ymax = float(np.max(fy)) if len(fy) else 1.0
        if ymax <= 1e-9:
            ymax = 1.0
        self.ax_freq.set_ylim(0, ymax * 1.2)

        peak_idx = int(np.argmax(fy)) if len(fy) else 0
        peak_freq = float(fx[peak_idx]) if len(fx) else 0.0
        peak_uv = float(fy[peak_idx]) if len(fy) else 0.0
        p2p = float(np.max(centered_uv) - np.min(centered_uv))
        rms = float(compute_rms(centered_uv))
        self.metrics.setText(
            f"主频: {peak_freq:.2f} Hz    峰值: {peak_uv:.2f} µV    "
            f"峰峰值: {p2p:.2f} µV    RMS值: {rms:.2f} µV"
        )
        self.canvas.draw_idle()


def main():
    app = QtWidgets.QApplication(sys.argv)
    win = MainWindow()
    win.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
