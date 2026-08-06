"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { User } from "@/lib/types";
import { Badge, Card, EmptyState, Spinner, Td, Th } from "@/components/ui";

export function Users() {
  const { data: users = [], isLoading } = useQuery({
    queryKey: ["users"],
    queryFn: () => api<User[]>("/api/v1/users"),
  });

  if (isLoading) return <Spinner />;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-stone-900">Users</h1>
        <p className="text-sm text-stone-500">
          EDITORs can write to the ledgers; VIEWERs are read-only
        </p>
      </div>

      <Card title="Accounts">
        {users.length === 0 ? (
          <EmptyState>No users found.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[400px] border-collapse">
              <thead className="border-b border-stone-200">
                <tr>
                  <Th>Username</Th>
                  <Th>Role</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-stone-50">
                    <Td className="font-medium text-stone-900">{user.username}</Td>
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
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
