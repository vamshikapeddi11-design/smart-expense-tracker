// ---------------------------------------------------------------------------
// QRScanner — reusable live camera QR reader built on html5-qrcode.
// Props:
//   onDecode(text)   — called once per unique payload
//   onError(message) — called for fatal errors (e.g. no camera / permission)
// The component exposes start/stop buttons and cleans the camera up on
// unmount. NOTE: browsers only grant camera access on HTTPS or localhost.
// ---------------------------------------------------------------------------

import { useEffect, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";

const REGION_ID = "qr-reader-region";

export default function QRScanner({ onDecode, onError }) {
  const scannerRef = useRef(null);
  const [scanning, setScanning] = useState(false);
  const [status, setStatus] = useState("Camera is off");
  const seenRef = useRef(new Set()); // ignore duplicate decode callbacks

  const start = async () => {
    if (scannerRef.current) return;
    try {
      const scanner = new Html5Qrcode(REGION_ID);
      scannerRef.current = scanner;
      setStatus("Requesting camera…");
      await scanner.start(
        { facingMode: "environment" }, // rear camera on phones
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (decodedText) => {
          // html5-qrcode can fire many times for the same code —
          // only report each unique payload once.
          if (!seenRef.current.has(decodedText)) {
            seenRef.current.add(decodedText);
            onDecode(decodedText);
            setStatus("QR code captured ✔");
          }
        },
        () => {
          /* per-frame decode failures are normal; ignore them */
        }
      );
      setScanning(true);
      setStatus("Point the camera at a QR code");
    } catch (err) {
      scannerRef.current = null;
      const msg =
        "Could not start the camera. Allow camera permission and use HTTPS or localhost.";
      setStatus(msg);
      onError?.(msg);
      console.error(err);
    }
  };

  const stop = async () => {
    try {
      if (scannerRef.current) {
        await scannerRef.current.stop();
        scannerRef.current.clear();
      }
    } catch {
      /* already stopped */
    } finally {
      scannerRef.current = null;
      setScanning(false);
      setStatus("Camera is off");
    }
  };

  // Always release the camera when the component unmounts.
  useEffect(() => {
    return () => {
      if (scannerRef.current) {
        scannerRef.current.stop().catch(() => {});
        scannerRef.current = null;
      }
    };
  }, []);

  return (
    <div>
      {/* html5-qrcode renders its video preview inside this div */}
      <div id={REGION_ID} className="qr-viewport" />
      <p className="hint" style={{ marginTop: "0.6rem" }}>
        {status}
      </p>
      {!scanning ? (
        <button className="btn btn-block" onClick={start}>
          📷 Start camera
        </button>
      ) : (
        <button className="btn btn-secondary btn-block" onClick={stop}>
          Stop camera
        </button>
      )}
    </div>
  );
}
