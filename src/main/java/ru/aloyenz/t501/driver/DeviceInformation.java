package ru.aloyenz.t501.driver;

public record DeviceInformation(byte interfaceNumber, byte entryPoint, int packetSize) {
}
