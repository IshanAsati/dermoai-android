"use client";

import { useEffect, useState } from "react";
import { timeRemaining } from "@/lib/format";

/**
 * Live "expires in" label. Starts from the server's clock so the first paint
 * matches the server render (no hydration mismatch), then ticks on the client.
 */
export function Countdown({ expiresAt, serverNow }: { expiresAt: number; serverNow: number }) {
  const [now, setNow] = useState(serverNow);

  useEffect(() => {
    const offset = Date.now() - serverNow;
    const tick = () => setNow(Date.now() - offset);
    const id = setInterval(tick, 15_000);
    return () => clearInterval(id);
  }, [serverNow]);

  const label = timeRemaining(expiresAt, now);
  return <span>{label === "expired" ? "Expired" : `Expires in ${label}`}</span>;
}
