import { redirect } from "next/navigation";
import { getDoctorContext } from "@/lib/doctor-context";

export default async function Home() {
  redirect((await getDoctorContext()).state === "signed-out" ? "/login" : "/dashboard");
}
