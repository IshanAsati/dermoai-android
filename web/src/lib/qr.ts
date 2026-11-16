import "server-only";
import QRCode from "qrcode";
import { isCompleteCode, deepLink } from "@/lib/domain/invite-codes";

/**
 * QR for an invite, rendered on the server as an SVG data URI.
 *
 * Generated locally and never through an image service: the code is a
 * credential that lets a clinician see someone's skin photos, and sending it to
 * a third party to be drawn would leak it. Dark pine on pure white keeps the
 * contrast scanners need; margin 4 is the spec's quiet zone, drawn inside the SVG.
 *
 * The payload is the `dermoai://invite/<code>` deep link because the Android
 * in-app scanner ignores any QR that does not start with it.
 */
export async function inviteQrDataUri(code: string): Promise<string | null> {
  if (!isCompleteCode(code)) return null;
  try {
    const svg = await QRCode.toString(deepLink(code), {
      type: "svg",
      errorCorrectionLevel: "M",
      margin: 4,
      color: { dark: "#123F33", light: "#FFFFFF" },
    });
    return `data:image/svg+xml;base64,${Buffer.from(svg, "utf8").toString("base64")}`;
  } catch {
    // A missing QR must degrade to "read the code aloud", never to a crashed page.
    return null;
  }
}
