package pl.bubson.notepadjw.fileManagerHelpers;

import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by Kuba on 2016-03-26.
 */
public class Item implements Comparable<Item>, Serializable{

    private String name, number, path;
    private Date date;
    private Type type;

    public Item(String name, String numberOfFilesOrBytes, Date dateOfModification, String absolutePath, Type type) {
        this.name = name;
        number = numberOfFilesOrBytes;
        date = dateOfModification;
        path = absolutePath;
        this.type = type;
    }

    public String getName()
    {
        return name;
    }
    public String getNumber()
    {
        return number;
    }
    public Date getDate()
    {
        return date;
    }
    public String getPath()
    {
        return path;
    }
    public Type getType() {
        return type;
    }

    @Override
    public int compareTo(@NonNull Item another) {
        if(getName() != null)
            return getName().toLowerCase().compareTo(another.getName().toLowerCase());
        else
            throw new IllegalArgumentException();
    }

    public enum Type {
        FILE, DIRECTORY, UP;
    };
}
