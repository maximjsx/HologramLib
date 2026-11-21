package com.maximde.hologramlib.persistence.importer;

record Position(String world, double x, double y, double z) {

    Position up(double height) {
        return new Position(world, x, y + height, z);
    }
}
