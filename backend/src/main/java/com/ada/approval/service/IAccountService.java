package com.ada.approval.service;

import com.ada.approval.entity.Account;

/**
 * Service interface
 *
 * @author Yan Min
 * @since 2023-12-15
 */
public interface IAccountService extends CrudService<Account> {
    Account findAccountByEmpId(Integer empId);

    /**
     * Resolve the account by username for Spring Security authentication Delegate password
     * verification to Spring Security
     *
     * @param userName
     * @return
     */
    public Account findAccountByUserName(String userName);

    /**
     * Change password
     *
     * @param oldPassowrd Current password
     * @param newPassword New password
     * @param repeatPassword New password confirmation
     */
    public void updatePassword(String oldPassowrd, String newPassword, String repeatPassword);
}
