package io.leavesfly.alphaforge.infrastructure.persistence.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 投资账户数据访问层
 */
@Mapper
public interface PortfolioAccountRepository {

    void insert(PortfolioAccount account);

    void update(PortfolioAccount account);

    PortfolioAccount findById(@Param("id") Long id);

    List<PortfolioAccount> findAll();

    List<PortfolioAccount> findByIsActive(@Param("isActive") Boolean isActive);

    void deleteById(@Param("id") Long id);

    long count();

    default PortfolioAccount save(PortfolioAccount account) {
        LocalDateTime now = LocalDateTime.now();
        if (account.getId() == null) {
            if (account.getCreatedAt() == null) account.setCreatedAt(now);
            account.setUpdatedAt(now);
            insert(account);
        } else {
            account.setUpdatedAt(now);
            update(account);
        }
        return account;
    }

    default Optional<PortfolioAccount> findByIdOpt(Long id) {
        return Optional.ofNullable(findById(id));
    }
}
