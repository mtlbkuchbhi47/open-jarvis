package com.openjarvis.science

import kotlin.math.*

/** Offline deterministic science calculations. Always exposes assumptions/units in the response. */
object ScienceEngine {
    fun calculate(query: String): String {
        val q = query.lowercase().replace("×", "*").replace("÷", "/")
        return when {
            q.contains("force") && q.contains("mass") && q.contains("acceleration") -> {
                val m=numberAfter(q,"mass"); val a=numberAfter(q,"acceleration"); "Force = ${m*a} N (F = ma)"
            }
            q.contains("kinetic") -> { val m=numberAfter(q,"mass"); val v=numberAfter(q,"velocity"); "Kinetic energy = ${0.5*m*v*v} J (KE = ½mv²)" }
            q.contains("potential") -> { val m=numberAfter(q,"mass"); val h=numberAfter(q,"height"); "Gravitational potential energy = ${m*9.80665*h} J (g = 9.80665 m/s²)" }
            q.contains("momentum") -> { val m=numberAfter(q,"mass"); val v=numberAfter(q,"velocity"); "Momentum = ${m*v} kg·m/s (p = mv)" }
            q.contains("ohm") || (q.contains("voltage") && q.contains("resistance")) -> { val v=numberAfter(q,"voltage"); val r=numberAfter(q,"resistance"); "Current = ${v/r} A (I = V/R)" }
            q.contains("power") && q.contains("voltage") && q.contains("current") -> { val v=numberAfter(q,"voltage"); val i=numberAfter(q,"current"); "Power = ${v*i} W (P = VI)" }
            q.contains("power") && q.contains("energy") && q.contains("time") -> { val e=numberAfter(q,"energy"); val t=numberAfter(q,"time"); "Power = ${e/t} W (P = E/t)" }
            q.contains("wavelength") && q.contains("frequency") -> { val f=numberAfter(q,"frequency"); "Wavelength = ${299792458.0/f} m (vacuum c = 299,792,458 m/s)" }
            q.contains("frequency") && q.contains("period") -> { val t=numberAfter(q,"period"); "Frequency = ${1/t} Hz (f = 1/T)" }
            q.contains("ideal gas") -> { val n=numberAfter(q,"moles"); val t=numberAfter(q,"temperature"); val volume=numberAfter(q,"volume"); "Pressure = ${(n*8.314462618*t)/volume} Pa (T must be kelvin)" }
            q.contains("ph") && q.contains("concentration") -> { val c=numberAfter(q,"concentration"); "pH = ${-log10(c)} (ideal dilute approximation)" }
            q.contains("celsius") && q.contains("fahrenheit") -> { val c=numberAfter(q,"celsius"); "Fahrenheit = ${c*9/5+32} °F" }
            q.contains("fahrenheit") && q.contains("celsius") -> { val f=numberAfter(q,"fahrenheit"); "Celsius = ${(f-32)*5/9} °C" }
            q.contains("kinematic") || (q.contains("distance") && q.contains("time") && q.contains("velocity")) -> { val d=numberAfter(q,"distance"); val t=numberAfter(q,"time"); "Average velocity = ${d/t} m/s" }
            else -> "Supported: force, kinetic/potential energy, momentum, Ohm's law, power, wavelength/frequency, ideal gas, pH, temperature, average velocity. Include named values and units."
        }
    }
    private fun numberAfter(text:String,key:String):Double {
        val r=Regex("${Regex.escape(key)}\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?)")
        return r.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: error("Missing numeric value for $key")
    }
}
