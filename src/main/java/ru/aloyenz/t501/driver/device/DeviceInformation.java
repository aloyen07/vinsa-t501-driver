package ru.aloyenz.t501.driver.device;

public record DeviceInformation(byte interfaceNumber, byte entryPoint, int packetSize) {
}
