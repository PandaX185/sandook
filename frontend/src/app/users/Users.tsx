"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { CreateUserRequest, Role, UpdateUserRequest, User } from "@/lib/types";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  Field,
  Input,
  Select,
  Spinner,
  Td,
  Th,
} from "@/components/ui";

const EMPTY_FORM: CreateUserRequest = {
  username: "",
  password: "",
  role: "VIEWER",
};

export function Users() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const isEditor = me?.role === "EDITOR";

  const [form, setForm] = useState<CreateUserRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: users = [], isLoading } = useQuery({
    queryKey: ["users"],
    queryFn: () => api<User[]>("/api/v1/users"),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["users"] });
  };

  const saveMutation = useMutation({
    mutationFn: (input: CreateUserRequest | UpdateUserRequest) =>
      editingId
        ? api<User>(`/api/v1/users/${editingId}`, {
            method: "PUT",
            body: JSON.stringify(input),
          })
        : api<User>("/api/v1/users", { method: "POST", body: JSON.stringify(input) }),
    onSuccess: () => {
      invalidate();
      setForm(EMPTY_FORM);
      setEditingId(null);
      setError(null);
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : "Save failed");
    },
  });

  const toggleActiveMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      api<User>(`/api/v1/users/${id}`, {
        method: "PUT",
        body: JSON.stringify({ active }),
      }),
    onSuccess: () => {
      invalidate();
      setError(null);
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : "Update failed");
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    const username = form.username.trim();
    if (editingId === null) {
      if (username.length < 3) {
        setError("Username must be at least 3 characters");
        return;
      }
      if (form.password.length < 8) {
        setError("Password must be at least 8 characters");
        return;
      }
      saveMutation.mutate({ username, password: form.password, role: form.role });
    } else {
      const payload: UpdateUserRequest = { role: form.role };
      if (form.password.length > 0) {
        if (form.password.length < 8) {
          setError("Password must be at least 8 characters");
          return;
        }
        payload.password = form.password;
      }
      saveMutation.mutate(payload);
    }
  }

  function startEdit(user: User) {
    setEditingId(user.id);
    setForm({ username: user.username, password: "", role: user.role });
    setError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError(null);
  }

  if (isLoading) return <Spinner />;

  const editingSelf = editingId !== null && me?.id === editingId;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">Users</h1>
        <p className="text-sm text-stone-500">
          EDITORs can write to the ledgers; VIEWERs are read-only. Accounts are
          deactivated, never deleted.
        </p>
      </div>

      {error ? <ErrorBanner message={error} /> : null}

      {isEditor ? (
        <Card title={editingId ? `Editing ${form.username}` : "New user"}>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="Username">
                <Input
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  placeholder="e.g. cashier"
                  required
                  readOnly={editingId !== null}
                  className={editingId !== null ? "opacity-60" : ""}
                />
              </Field>
              <Field
                label="Password"
                hint={
                  editingId !== null
                    ? "Leave blank to keep the current password"
                    : "At least 8 characters"
                }
              >
                <Input
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  placeholder={editingId !== null ? "••••••••" : ""}
                />
              </Field>
              <Field
                label="Role"
                hint={editingSelf ? "You cannot demote your own account" : undefined}
              >
                <Select
                  value={form.role}
                  onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
                >
                  <option value="VIEWER" disabled={editingSelf}>
                    VIEWER — read-only
                  </option>
                  <option value="EDITOR">EDITOR — full access</option>
                </Select>
              </Field>
            </div>
            <div className="flex items-center gap-2">
              <Button type="submit" disabled={saveMutation.isPending}>
                {editingId !== null ? "Save changes" : "Create user"}
              </Button>
              {editingId !== null ? (
                <Button type="button" variant="secondary" onClick={cancelEdit}>
                  Cancel
                </Button>
              ) : null}
            </div>
          </form>
        </Card>
      ) : null}

      <Card
        title="Accounts"
        action={
          users.length > 0 ? (
            <span className="text-xs text-stone-400">
              {users.filter((u) => u.active).length} active
            </span>
          ) : null
        }
      >
        {users.length === 0 ? (
          <EmptyState>No users found.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Username</Th>
                  <Th>Role</Th>
                  <Th>Status</Th>
                  {isEditor ? <Th>Actions</Th> : null}
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {users.map((user) => {
                  const isSelf = me?.username === user.username;
                  return (
                    <tr key={user.id} className="hover:bg-stone-50">
                      <Td className="font-medium text-stone-900">
                        {user.username}
                        {isSelf ? (
                          <span className="ml-2 text-xs text-stone-400">(you)</span>
                        ) : null}
                      </Td>
                      <Td>
                        <Badge tone={user.role === "EDITOR" ? "green" : "amber"}>
                          {user.role}
                        </Badge>
                      </Td>
                      <Td>
                        <Badge tone={user.active ? "green" : "stone"}>
                          {user.active ? "Active" : "Inactive"}
                        </Badge>
                      </Td>
                      {isEditor ? (
                        <Td className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="secondary"
                              className="px-2.5 py-1 text-xs"
                              onClick={() => startEdit(user)}
                            >
                              Edit
                            </Button>
                            {!isSelf ? (
                              <Button
                                variant={user.active ? "danger" : "secondary"}
                                className="px-2.5 py-1 text-xs"
                                disabled={toggleActiveMutation.isPending}
                                onClick={() =>
                                  toggleActiveMutation.mutate({
                                    id: user.id,
                                    active: !user.active,
                                  })
                                }
                              >
                                {user.active ? "Deactivate" : "Reactivate"}
                              </Button>
                            ) : null}
                          </div>
                        </Td>
                      ) : null}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
