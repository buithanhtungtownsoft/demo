package com.example.demo.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("tb_user")
public class UserModel {

    @Id
    private UserId id;

    @Column("user_name")
    private String userName;

    private String password;
    private String email;
    private String authority;

    @Column("position_level")
    private String positionLevel;

    @Column("job_title")
    private String jobTitle;

    @Column("use_yn")
    private String useYn;

    @Column("created_at")
    private java.time.LocalDateTime createdAt;

    @Column("updated_at")
    private java.time.LocalDateTime updatedAt;

    @Column("created_user")
    private String createdUser;

    @Column("updated_user")
    private String updatedUser;

}
