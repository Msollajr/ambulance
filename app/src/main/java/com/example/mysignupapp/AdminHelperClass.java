package com.example.mysignupapp;

public class AdminHelperClass {

    String name;
    String email;
    String password;
    String phone;
    String org_name;

    public AdminHelperClass(String name, String email, String password, String phone, String org_name) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.org_name = org_name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOrg_name() {
        return org_name;
    }

    public void setOrg_name(String org_name) {
        this.org_name = org_name;
    }


}
