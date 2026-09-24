import { pedir } from "./client";

/** Los numeros de la cartera de la empresa de la sesion. */
export const resumenDeCartera = () => pedir("/analytics/summary");
