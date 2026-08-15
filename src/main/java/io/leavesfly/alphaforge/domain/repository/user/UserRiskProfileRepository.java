package io.leavesfly.alphaforge.domain.repository.user;

import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户风险画像数据访问层 — 单行记录（id=1），UPSERT 语义
 */
@Mapper
public interface UserRiskProfileRepository {

    /** 查询画像（无记录返回 null） */
    UserRiskProfile find();

    /** 保存画像（存在则更新） */
    int upsert(UserRiskProfile profile);
}
