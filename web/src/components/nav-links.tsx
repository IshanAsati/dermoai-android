"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { QrIcon, UserIcon, UsersIcon } from "./icons";

const ITEMS = [
  { href: "/dashboard", label: "Patients", Icon: UsersIcon, match: ["/dashboard", "/patients"] },
  { href: "/invites", label: "Pair patient", Icon: QrIcon, match: ["/invites"] },
  { href: "/profile", label: "Profile", Icon: UserIcon, match: ["/profile"] },
];

export function NavLinks() {
  const pathname = usePathname();
  return (
    <>
      {ITEMS.map(({ href, label, Icon, match }) => {
        const active = match.some((m) => pathname === m || pathname.startsWith(`${m}/`));
        return (
          <Link key={href} href={href} className="nav-link" aria-current={active ? "page" : undefined}>
            <span className="nav-icon">
              <Icon />
            </span>
            <span>{label}</span>
          </Link>
        );
      })}
    </>
  );
}
