export type Role = "SUPERADMIN" | "CUSTOMER" | "FOREMAN" | "BUILDER" | "SUPPLIER" | "COMPANY"

export interface User {
    email: string;
    firstName: string;
    lastName: string;
    role: Role;
    companyId?: string;
}

export interface CreateUserRequest extends User {
    username: string;
};

export type UpdateUserRequest = User;

export interface UserResponse extends User {
    id: string;
    username: string;
    active: boolean;
    createdAt: string;
    updatedAt?: string;
}

export interface SetPasswordRequest {
    password: string;
}